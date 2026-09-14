import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { loadStripe } from '@stripe/stripe-js';
import { useState } from 'react';
import { useCheckoutConfig } from '../../features/stripe/useCheckoutConfig';

function PayButton() {
  const stripe = useStripe();
  const elements = useElements();
  const [status, setStatus] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const pay = async () => {
    if (!stripe || !elements) return;
    setSubmitting(true);
    const result = await stripe.confirmPayment({ elements, redirect: 'if_required' });
    setSubmitting(false);
    if (result.error) {
      setStatus(result.error.message ?? 'Payment could not be confirmed.');
    } else {
      setStatus(`Payment intent status: ${result.paymentIntent?.status}. The booking becomes CONFIRMED once the Stripe webhook reports success.`);
    }
  };

  return (
    <div>
      <button type="button" onClick={pay} disabled={!stripe || submitting}>{submitting ? 'Processing…' : 'Pay'}</button>
      {status && <p className="console-muted">{status}</p>}
    </div>
  );
}

export default function StripePaymentSection({ clientSecret }: { clientSecret: string }) {
  const { data: config, isLoading, error } = useCheckoutConfig();

  if (isLoading) return <p className="console-muted">Loading payment form…</p>;
  if (error || !config) return <p className="console-error">Could not load Stripe configuration.</p>;

  const stripePromise = loadStripe(config.stripePublishableKey);

  return (
    <Elements stripe={stripePromise} options={{ clientSecret }}>
      <PaymentElement />
      <PayButton />
    </Elements>
  );
}
