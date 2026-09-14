import { Link } from 'react-router-dom';
import { useBookings } from '../../features/booking/useBookings';
import { selectedOrganization } from '../../features/organizations/organizationStorage';

export default function BookingsPage() {
  const organizationId = selectedOrganization();
  const { data: bookings = [], isLoading, error } = useBookings(organizationId);

  if (!organizationId) {
    return <p>Select an <Link to="/console/organizations">organization</Link> first.</p>;
  }

  return (
    <div>
      <h2>Bookings</h2>
      {isLoading && <p className="console-muted">Loading…</p>}
      {error && <p className="console-error">Could not load bookings.</p>}
      <ul className="console-list">
        {bookings.map((booking) => (
          <li key={booking.id}>
            <span>{new Date(booking.startTime).toLocaleString()} – {new Date(booking.endTime).toLocaleTimeString()}</span>
            <span>{booking.status}</span>
          </li>
        ))}
        {bookings.length === 0 && !isLoading && <li className="console-muted">No bookings yet.</li>}
      </ul>
    </div>
  );
}
