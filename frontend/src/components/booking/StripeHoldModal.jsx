import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Elements, ExpressCheckoutElement, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { loadStripe } from '@stripe/stripe-js';
import { CreditCard, ShieldCheck, X } from 'lucide-react';

import useBodyScrollLock from '../../hooks/useBodyScrollLock';
import { useColorScheme } from '../../context/ColorSchemeContext';
import GoldButton from '../ui/GoldButton';

const stripePromiseCache = new Map();
const DEFAULT_BILLING_COUNTRY = 'IE';
const STRIPE_INSTANCE_OPTIONS = {
    developerTools: {
        assistant: {
            enabled: false
        }
    }
};

function buildStripeAppearance(colorScheme) {
    const isLight = colorScheme === 'light';
    const neutralSurface = isLight ? '#ffffff' : '#000000';
    const neutralText = isLight ? '#111111' : '#ffffff';
    const neutralTextSecondary = isLight ? '#4f4f4f' : '#d2d2d2';
    const neutralPlaceholder = isLight ? '#7a7a7a' : '#8e8e8e';
    const neutralBorder = isLight ? '#e6ddd2' : '#262626';
    const neutralInsetShadow = isLight
        ? '0 0 0 1px #e6ddd2, 0 1px 2px rgba(17, 17, 17, 0.04)'
        : '0 0 0 1px #262626, 0 1px 2px rgba(0, 0, 0, 0.4)';
    return {
        theme: 'flat',
        variables: {
            colorText: neutralText,
            colorTextSecondary: neutralTextSecondary,
            colorTextPlaceholder: neutralPlaceholder,
            colorBackground: neutralSurface,
            colorPrimary: isLight ? '#111111' : '#ffffff',
            colorPrimaryText: neutralText,
            colorDanger: isLight ? '#b34256' : '#ffbdca',
            colorSuccess: '#3f7c53',
            accessibleColorOnColorPrimary: isLight ? '#ffffff' : '#000000',
            accessibleColorOnColorBackground: neutralText,
            iconColor: neutralText,
            iconHoverColor: neutralText,
            iconChevronDownColor: neutralText,
            iconCardCvcColor: neutralText,
            tabIconColor: neutralText,
            tabIconSelectedColor: neutralText,
            logoColor: isLight ? 'dark' : 'light',
            tabLogoColor: isLight ? 'dark' : 'light',
            tabLogoSelectedColor: isLight ? 'dark' : 'light',
            blockLogoColor: isLight ? 'dark' : 'light',
            borderRadius: '18px',
            fontFamily: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
        },
        rules: {
            '.Tab': {
                backgroundColor: neutralSurface,
                boxShadow: neutralInsetShadow
            },
            '.Tab--selected': {
                backgroundColor: neutralSurface,
                boxShadow: `0 0 0 1px ${isLight ? '#111111' : '#ffffff'}`
            },
            '.TabLabel': {
                color: 'var(--colorText)'
            },
            '.TabLabel--selected': {
                color: 'var(--colorPrimaryText)'
            },
            '.TabIcon': {
                color: 'var(--iconColor)'
            },
            '.TabIcon--selected': {
                color: 'var(--iconColor)'
            },
            '.Block': {
                backgroundColor: neutralSurface,
                boxShadow: neutralInsetShadow,
                color: 'var(--colorText)'
            },
            '.BlockAction': {
                color: 'var(--colorText)'
            },
            '.BlockDivider': {
                backgroundColor: neutralBorder
            },
            '.Input': {
                backgroundColor: neutralSurface,
                boxShadow: neutralInsetShadow
            },
            '.CodeInput': {
                backgroundColor: neutralSurface,
                boxShadow: neutralInsetShadow
            },
            '.PickerItem': {
                backgroundColor: neutralSurface,
                boxShadow: neutralInsetShadow
            },
            '.PickerItem--selected': {
                backgroundColor: neutralSurface,
                boxShadow: `0 0 0 1px ${isLight ? '#111111' : '#ffffff'}`
            },
            '.AccordionItem': {
                backgroundColor: neutralSurface,
                boxShadow: neutralInsetShadow
            },
            '.Label': {
                color: 'var(--colorText)'
            }
        }
    };
}

function getStripePromise(publishableKey) {
    if (!stripePromiseCache.has(publishableKey)) {
        stripePromiseCache.set(publishableKey, loadStripe(publishableKey, STRIPE_INSTANCE_OPTIONS));
    }

    return stripePromiseCache.get(publishableKey);
}

function toMinorUnits(value) {
    return Math.round(Number(value) * 100);
}

function resolveBillingCountry(customer) {
    const country = customer?.customerCountry || customer?.country;

    if (typeof country === 'string' && country.trim()) {
        return country.trim().toUpperCase();
    }

    return DEFAULT_BILLING_COUNTRY;
}

async function finalizePaymentIntent({
    stripe,
    checkoutSession,
    onConfirm,
    onErrorChange,
    paymentLabel,
    walletEvent
}) {
    if (!checkoutSession?.paymentIntentId) {
        const message = `${paymentLabel} could not be created. Please try again.`;
        onErrorChange(message);
        walletEvent?.paymentFailed?.({
            reason: 'fail',
            message
        });
        return;
    }

    let paymentIntentId = checkoutSession.paymentIntentId;
    let paymentStatus = checkoutSession.paymentStatus;

    if (paymentStatus === 'requires_action') {
        if (!stripe || !checkoutSession.clientSecret) {
            const message = `${paymentLabel} requires additional authentication, but Stripe could not continue.`;
            onErrorChange(message);
            walletEvent?.paymentFailed?.({
                reason: 'fail',
                message
            });
            return;
        }

        const { error, paymentIntent } = await stripe.handleNextAction({
            clientSecret: checkoutSession.clientSecret
        });

        if (error) {
            const message = error.message || `${paymentLabel} authentication failed. Please try again.`;
            onErrorChange(message);
            walletEvent?.paymentFailed?.({
                reason: 'fail',
                message
            });
            return;
        }

        paymentIntentId = paymentIntent?.id || paymentIntentId;
        paymentStatus = paymentIntent?.status || paymentStatus;
    }

    if (paymentStatus !== 'succeeded') {
        const message = `${paymentLabel} finished with unexpected status: ${paymentStatus || 'unknown'}. Please try again.`;
        onErrorChange(message);
        walletEvent?.paymentFailed?.({
            reason: 'fail',
            message
        });
        return;
    }

    await onConfirm(paymentIntentId);
}

async function createConfirmationTokenForElements({ stripe, elements, customer }) {
    if (!stripe || !elements) {
        throw new Error('Stripe checkout is still loading. Please wait a moment and try again.');
    }

    const submission = await elements.submit();
    if (submission?.error) {
        throw new Error(submission.error.message || 'Please complete the payment details first.');
    }

    const { error, confirmationToken } = await stripe.createConfirmationToken({
        elements,
        params: {
            payment_method_data: {
                billing_details: {
                    name: customer.customerName || undefined,
                    email: customer.customerEmail || undefined,
                    phone: customer.customerPhone || undefined,
                    address: {
                        country: resolveBillingCountry(customer)
                    }
                }
            },
            return_url: window.location.href
        }
    });

    if (error) {
        throw new Error(error.message || 'Stripe could not prepare the payment details. Please try again.');
    }

    if (!confirmationToken?.id) {
        throw new Error('Stripe did not return a confirmation token. Please try again.');
    }

    return confirmationToken.id;
}

const StripeWalletCheckout = memo(function StripeWalletCheckout({
    customer,
    loading,
    onBusyChange,
    onErrorChange,
    onAuthorize,
    onConfirm
}) {
    const stripe = useStripe();
    const elements = useElements();
    const [walletVisibility, setWalletVisibility] = useState('pending');

    const expressCheckoutOptions = useMemo(
        () => ({
            buttonHeight: 50,
            buttonTheme: {
                applePay: 'black',
                googlePay: 'black'
            },
            buttonType: {
                applePay: 'plain',
                googlePay: 'pay'
            },
            emailRequired: true,
            layout: {
                maxColumns: 2,
                maxRows: 1,
                overflow: 'auto'
            },
            paymentMethodOrder: ['apple_pay', 'google_pay', 'link'],
            phoneNumberRequired: true
        }),
        []
    );

    const handleWalletConfirm = useCallback(async (event) => {
        onErrorChange('');
        onBusyChange(true);

        try {
            const confirmationTokenId = await createConfirmationTokenForElements({
                stripe,
                elements,
                customer
            });
            const checkoutSession = await onAuthorize(confirmationTokenId);
            const paymentLabel = event.expressPaymentType === 'google_pay'
                ? 'Google Pay payment'
                : event.expressPaymentType === 'link'
                    ? 'Link payment'
                    : event.expressPaymentType === 'apple_pay'
                        ? 'Apple Pay payment'
                        : 'Express checkout payment';

            await finalizePaymentIntent({
                stripe,
                checkoutSession,
                onConfirm,
                onErrorChange,
                paymentLabel,
                walletEvent: event
            });
        } catch (error) {
            const message = error.message || 'Wallet payment failed. Please try again.';
            onErrorChange(message);
            event.paymentFailed?.({
                reason: 'fail',
                message
            });
        } finally {
            onBusyChange(false);
        }
    }, [customer, elements, onAuthorize, onBusyChange, onConfirm, onErrorChange, stripe]);

    const handleExpressReady = useCallback(({ availablePaymentMethods }) => {
        const hasWallet = Boolean(
            availablePaymentMethods?.applePay ||
            availablePaymentMethods?.googlePay ||
            availablePaymentMethods?.link
        );
        setWalletVisibility(hasWallet ? 'visible' : 'collapsed');
    }, []);

    const handleExpressClick = useCallback((event) => {
        event.resolve();
    }, []);

    const handleExpressLoadError = useCallback(({ error }) => {
        setWalletVisibility('collapsed');
        onErrorChange(error?.message || 'Wallet checkout could not be loaded.');
    }, [onErrorChange]);

    return (
        <div
            className={[
                'stripe-applepay-action',
                walletVisibility === 'pending' ? 'stripe-applepay-action-pending' : '',
                walletVisibility === 'collapsed' ? 'stripe-applepay-action-hidden' : ''
            ]
                .filter(Boolean)
                .join(' ')}
        >
            <ExpressCheckoutElement
                options={expressCheckoutOptions}
                onReady={handleExpressReady}
                onClick={handleExpressClick}
                onLoadError={handleExpressLoadError}
                onConfirm={(event) => {
                    if (loading) {
                        return;
                    }

                    void handleWalletConfirm(event);
                }}
            />
        </div>
    );
});

const StripeCardCheckout = memo(function StripeCardCheckout({
    customer,
    paymentAmountLabel,
    loading,
    onBusyChange,
    onErrorChange,
    onClose,
    onAuthorize,
    onConfirm,
    walletAction
}) {
    const stripe = useStripe();
    const elements = useElements();

    const paymentElementOptions = useMemo(
        () => ({
            business: {
                name: 'Royal Chair Barber & Beauty Salon'
            },
            defaultValues: {
                billingDetails: {
                    name: customer.customerName || '',
                    email: customer.customerEmail || '',
                    phone: customer.customerPhone || ''
                }
            },
            fields: {
                billingDetails: {
                    name: 'never',
                    email: 'never',
                    phone: 'never',
                    address: 'auto'
                }
            },
            paymentMethodOrder: ['card'],
            wallets: {
                applePay: 'never',
                googlePay: 'never',
                link: 'never'
            }
        }),
        [customer.customerEmail, customer.customerName, customer.customerPhone]
    );

    const handlePay = useCallback(async () => {
        onErrorChange('');
        onBusyChange(true);

        try {
            const confirmationTokenId = await createConfirmationTokenForElements({
                stripe,
                elements,
                customer
            });
            const checkoutSession = await onAuthorize(confirmationTokenId);

            await finalizePaymentIntent({
                stripe,
                checkoutSession,
                onConfirm,
                onErrorChange,
                paymentLabel: 'Card payment'
            });
        } catch (error) {
            onErrorChange(error.message || 'Unable to complete this payment. Please try again.');
        } finally {
            onBusyChange(false);
        }
    }, [customer, elements, onAuthorize, onBusyChange, onConfirm, onErrorChange, stripe]);

    const handlePaymentElementChange = useCallback((event) => {
        onErrorChange(event.error?.message || '');
    }, [onErrorChange]);

    const handlePaymentElementLoadError = useCallback(({ error }) => {
        onErrorChange(error?.message || 'Card checkout could not be loaded.');
    }, [onErrorChange]);

    return (
        <>
            <div className="stripe-payment-shell">
                <div className="stripe-payment-shell-header">
                    <span>
                        <CreditCard size={14} /> Card Details
                    </span>
                </div>
                <PaymentElement
                    options={paymentElementOptions}
                    onChange={handlePaymentElementChange}
                    onLoadError={handlePaymentElementLoadError}
                />
            </div>

            {walletAction ? (
                <div className="stripe-wallet-actions-row">
                    {walletAction}
                </div>
            ) : null}

            <div className={`stripe-modal-actions ${walletAction ? 'stripe-modal-actions-with-wallet' : ''}`}>
                <GoldButton type="button" onClick={onClose} disabled={loading}>
                    Cancel
                </GoldButton>

                <GoldButton
                    type="button"
                    disabled={loading || !stripe || !elements}
                    onClick={() => {
                        void handlePay();
                    }}
                >
                    {loading ? 'Processing payment...' : `Pay ${paymentAmountLabel}`}
                </GoldButton>
            </div>
        </>
    );
});

export default function StripeHoldModal({
    open,
    prewarm = false,
    paymentAmountLabel,
    paymentAmountValue,
    currency,
    customer,
    publishableKey,
    loading = false,
    serverError = '',
    onClose,
    onAuthorize,
    onConfirm
}) {
    const { colorScheme } = useColorScheme();
    const [checkoutError, setCheckoutError] = useState('');
    const [confirming, setConfirming] = useState(false);
    const onCloseRef = useRef(onClose);
    const onAuthorizeRef = useRef(onAuthorize);
    const onConfirmRef = useRef(onConfirm);
    const shouldMountCheckout = open || prewarm;
    useBodyScrollLock(open);

    useEffect(() => {
        onCloseRef.current = onClose;
    }, [onClose]);

    useEffect(() => {
        onAuthorizeRef.current = onAuthorize;
    }, [onAuthorize]);

    useEffect(() => {
        onConfirmRef.current = onConfirm;
    }, [onConfirm]);

    useEffect(() => {
        if (!open) {
            setCheckoutError('');
            setConfirming(false);
        }
    }, [open]);

    const stripePromise = useMemo(
        () => (publishableKey ? getStripePromise(publishableKey) : null),
        [publishableKey]
    );
    const appearance = useMemo(() => buildStripeAppearance(colorScheme), [colorScheme]);
    const cardElementsOptions = useMemo(() => {
        if (paymentAmountValue == null || !currency) {
            return null;
        }

        return {
            mode: 'payment',
            amount: toMinorUnits(paymentAmountValue),
            currency: currency.toLowerCase(),
            paymentMethodTypes: ['card'],
            appearance
        };
    }, [appearance, currency, paymentAmountValue]);
    const walletElementsOptions = useMemo(() => {
        if (paymentAmountValue == null || !currency) {
            return null;
        }

        return {
            mode: 'payment',
            amount: toMinorUnits(paymentAmountValue),
            currency: currency.toLowerCase(),
            paymentMethodTypes: ['card']
        };
    }, [currency, paymentAmountValue]);

    const isBusy = loading || confirming;
    const checkoutCustomer = useMemo(
        () => ({
            customerName: customer.customerName,
            customerEmail: customer.customerEmail,
            customerPhone: customer.customerPhone
        }),
        [customer.customerEmail, customer.customerName, customer.customerPhone]
    );

    const handleClose = useCallback(() => {
        onCloseRef.current?.();
    }, []);

    const handleAuthorize = useCallback((confirmationTokenId) => {
        return onAuthorizeRef.current?.(confirmationTokenId);
    }, []);

    const handleConfirm = useCallback((paymentIntentId) => {
        return onConfirmRef.current?.(paymentIntentId);
    }, []);

    const checkoutBootstrapError = !publishableKey
        ? 'Stripe checkout is not configured yet. Please add a publishable key.'
        : paymentAmountValue == null
            ? 'The selected service price is unavailable. Please go back and choose a treatment again.'
            : !currency
                ? 'Stripe currency is missing. Please try again.'
                : '';

    if (!shouldMountCheckout) {
        return null;
    }

    return (
        <div
            className={`stripe-modal-backdrop ${open ? '' : 'stripe-modal-backdrop-hidden'}`}
            role="presentation"
            aria-hidden={!open}
            onClick={open && !isBusy ? onClose : undefined}
        >
            <div
                className={`stripe-modal-card ${open ? '' : 'stripe-modal-card-hidden'}`}
                role={open ? 'dialog' : undefined}
                aria-modal={open ? 'true' : undefined}
                aria-labelledby={open ? 'stripe-modal-title' : undefined}
                onClick={(event) => event.stopPropagation()}
            >
                <div className="stripe-modal-header">
                    <div>
                        <div className="booking-summary-badge">Secure Stripe Payment</div>
                        <h2 id="stripe-modal-title">Complete your booking payment</h2>
                    </div>

                    <button
                        type="button"
                        className="stripe-modal-close"
                        onClick={onClose}
                        disabled={isBusy}
                        aria-label="Close Stripe checkout"
                    >
                        <X size={18} />
                    </button>
                </div>

                <div className="stripe-modal-copy">
                    <p>
                        Stripe will charge <strong>{paymentAmountLabel}</strong> now and your booking will be finalized
                        automatically as soon as Stripe confirms the payment.
                    </p>
                    <p>Enter your bank card details or use Apple Pay / Google Pay / Link to complete the payment.</p>
                </div>

                <div className="stripe-modal-form">
                    <div className="stripe-modal-info-grid">
                        <div>
                            <span>
                                <CreditCard size={14} /> Cardholder
                            </span>
                            <strong>{customer.customerName || '--'}</strong>
                        </div>
                        <div>
                            <span>
                                <ShieldCheck size={14} /> Email
                            </span>
                            <strong>{customer.customerEmail || '--'}</strong>
                        </div>
                    </div>

                    {checkoutBootstrapError ? (
                        <div className="booking-status-card booking-status-card-error">{checkoutBootstrapError}</div>
                    ) : (
                        <Elements
                            key={`card-checkout-${currency}-${paymentAmountValue}-${colorScheme}`}
                            stripe={stripePromise}
                            options={cardElementsOptions}
                        >
                            <StripeCardCheckout
                                customer={checkoutCustomer}
                                paymentAmountLabel={paymentAmountLabel}
                                loading={isBusy}
                                onBusyChange={setConfirming}
                                onErrorChange={setCheckoutError}
                                onClose={handleClose}
                                onAuthorize={handleAuthorize}
                                onConfirm={handleConfirm}
                                walletAction={
                                    walletElementsOptions ? (
                                        <Elements
                                            key={`wallet-checkout-${currency}-${paymentAmountValue}-${colorScheme}`}
                                            stripe={stripePromise}
                                            options={walletElementsOptions}
                                        >
                                            <StripeWalletCheckout
                                                customer={checkoutCustomer}
                                                loading={isBusy}
                                                onBusyChange={setConfirming}
                                                onErrorChange={setCheckoutError}
                                                onAuthorize={handleAuthorize}
                                                onConfirm={handleConfirm}
                                            />
                                        </Elements>
                                    ) : null
                                }
                            />
                        </Elements>
                    )}

                    {checkoutError ? <div className="booking-status-card booking-status-card-error">{checkoutError}</div> : null}
                    {serverError ? <div className="booking-status-card booking-status-card-error">{serverError}</div> : null}

                    {checkoutBootstrapError ? (
                        <div className="stripe-modal-actions">
                            <GoldButton type="button" onClick={onClose} disabled={isBusy}>
                                Cancel
                            </GoldButton>
                            <GoldButton type="button" disabled>
                                Apple Pay
                            </GoldButton>
                            <GoldButton type="button" disabled>
                                {`Pay ${paymentAmountLabel}`}
                            </GoldButton>
                        </div>
                    ) : null}
                </div>
            </div>
        </div>
    );
}
