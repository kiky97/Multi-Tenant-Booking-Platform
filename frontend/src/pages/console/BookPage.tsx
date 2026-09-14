import axios from 'axios';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { createBooking, createHold } from '../../features/booking/api';
import { selectedOrganization } from '../../features/organizations/organizationStorage';
import { useServices } from '../../features/services/useServices';
import { useStaff } from '../../features/staff/useStaff';
import type { Booking, Hold } from '../../types/booking';
import StripePaymentSection from './StripePaymentSection';

const describeError = (err: unknown, fallback: string): string => {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as { message?: string } | undefined;
    return `${fallback} (HTTP ${err.response?.status ?? '?'}${body?.message ? `: ${body.message}` : ''})`;
  }
  return fallback;
};

const toIso = (localDateTime: string): string => (localDateTime ? new Date(localDateTime).toISOString() : '');

export default function BookPage() {
  const organizationId = selectedOrganization();
  const { data: services = [] } = useServices(organizationId);
  const { data: staff = [] } = useStaff(organizationId);

  const [serviceId, setServiceId] = useState('');
  const [staffId, setStaffId] = useState('');
  const [startLocal, setStartLocal] = useState('');
  const [customerId, setCustomerId] = useState('');

  const [hold, setHold] = useState<Hold | null>(null);
  const [holding, setHolding] = useState(false);
  const [holdError, setHoldError] = useState<string | null>(null);

  const [booking, setBooking] = useState<Booking | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [bookingError, setBookingError] = useState<string | null>(null);

  if (!organizationId) {
    return <p>Select an <Link to="/console/organizations">organization</Link> first.</p>;
  }

  const canHold = Boolean(serviceId && staffId && startLocal);

  const requestHold = async () => {
    setHoldError(null);
    setHolding(true);
    try {
      const result = await createHold({ organizationId, staffId, serviceId, startTime: toIso(startLocal) });
      setHold(result);
    } catch (err) {
      setHoldError(describeError(err, 'Could not hold this slot — it may already be taken.'));
    } finally {
      setHolding(false);
    }
  };

  const confirmBooking = async () => {
    if (!hold) return;
    setBookingError(null);
    setConfirming(true);
    try {
      const result = await createBooking(organizationId, {
        customerId,
        staffId,
        serviceId,
        startTime: toIso(startLocal),
        holdToken: hold.holdToken,
      });
      setBooking(result);
    } catch (err) {
      setBookingError(describeError(err, 'Could not confirm the booking.'));
    } finally {
      setConfirming(false);
    }
  };

  return (
    <div>
      <h2>Book a slot</h2>

      <section className="console-step">
        <h3>1. Choose service, staff, and time</h3>
        <div className="console-form">
          <label>
            Service
            <select value={serviceId} onChange={(event) => setServiceId(event.target.value)} disabled={Boolean(hold)}>
              <option value="">Select a service</option>
              {services.map((service) => (
                <option key={service.id} value={service.id}>{service.name} ({service.durationMinutes} min)</option>
              ))}
            </select>
          </label>
          <label>
            Staff
            <select value={staffId} onChange={(event) => setStaffId(event.target.value)} disabled={Boolean(hold)}>
              <option value="">Select a staff member</option>
              {staff.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
            </select>
          </label>
          <label>
            Start time
            <input type="datetime-local" value={startLocal} onChange={(event) => setStartLocal(event.target.value)} disabled={Boolean(hold)} />
          </label>
          {!hold && (
            <button type="button" onClick={requestHold} disabled={!canHold || holding}>
              {holding ? 'Holding…' : 'Hold this slot (5 min)'}
            </button>
          )}
          {holdError && <p className="console-error">{holdError}</p>}
        </div>
      </section>

      {hold && (
        <section className="console-step">
          <h3>2. Confirm the booking</h3>
          <p className="console-muted">
            Slot held until {new Date(hold.expiresAt).toLocaleTimeString()}. This lock is what stops another
            customer from double-booking the same time — see the hold token below.
          </p>
          <p className="console-muted">Hold token: {hold.holdToken}</p>
          {!booking && (
            <div className="console-form">
              <label>
                Customer ID
                <input
                  type="text"
                  placeholder="Paste the Customer ID from registration"
                  value={customerId}
                  onChange={(event) => setCustomerId(event.target.value)}
                />
              </label>
              <button type="button" onClick={confirmBooking} disabled={!customerId || confirming}>
                {confirming ? 'Confirming…' : 'Confirm booking'}
              </button>
              {bookingError && <p className="console-error">{bookingError}</p>}
            </div>
          )}
        </section>
      )}

      {booking && (
        <section className="console-step">
          <h3>3. Pay</h3>
          <p className="console-muted">Booking {booking.id} is {booking.status}. It becomes CONFIRMED only once Stripe's webhook reports a successful payment.</p>
          {booking.clientSecret ? (
            <StripePaymentSection clientSecret={booking.clientSecret} />
          ) : (
            <p className="console-muted">No payment is required for this booking.</p>
          )}
        </section>
      )}
    </div>
  );
}
