import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { clearSession, getSession } from '../../features/auth/session';
import { selectedOrganization } from '../../features/organizations/organizationStorage';
import { useOrganizations } from '../../features/organizations/useOrganizations';
import './console.css';

const navItems = [
  { to: '/console/organizations', label: 'Organizations' },
  { to: '/console/setup', label: 'Setup' },
  { to: '/console/book', label: 'Book' },
  { to: '/console/bookings', label: 'Bookings' },
];

export default function ConsoleLayout() {
  const navigate = useNavigate();
  const session = getSession();
  const organizationId = selectedOrganization();
  const { data: organizations = [] } = useOrganizations();
  const currentOrganization = organizations.find((org) => org.id === organizationId);

  const logout = () => {
    clearSession();
    navigate('/console/login');
  };

  return (
    <div className="console">
      <header className="console-header">
        <span className="console-title">Booking Console</span>
        {session && (
          <nav className="console-nav">
            {navItems.map((item) => (
              <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : '')}>
                {item.label}
              </NavLink>
            ))}
          </nav>
        )}
        <div className="console-status">
          {session && (
            <>
              <span className="console-role">{session.role}</span>
              <span className="console-org">
                {currentOrganization ? currentOrganization.name : organizationId ? organizationId : 'No organization selected'}
              </span>
              <button type="button" onClick={logout}>Log out</button>
            </>
          )}
        </div>
      </header>
      <main className="console-main">
        <Outlet />
      </main>
    </div>
  );
}
