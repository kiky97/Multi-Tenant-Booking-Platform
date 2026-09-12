import { NavLink } from 'react-router-dom';
import { CalendarDays, LogOut, RefreshCw, Scissors, Sparkles, Store } from 'lucide-react';
import AdminColorSchemeToggle from './AdminColorSchemeToggle';

const NAV_ITEMS = [
    { to: '/bookings', label: 'Bookings', icon: CalendarDays },
    { to: '/employees', label: 'Employees', icon: Scissors },
    { to: '/treatments', label: 'Treatments', icon: Sparkles },
    { to: '/salon', label: 'Salon', icon: Store }
];

export default function AdminLayout({
    children,
    loading,
    onRefresh,
    onLogout,
    globalError,
    globalSuccess
}) {
    return (
        <main className="page admin-page">
            <header className="admin-header glass-card gold-border">
                <div className="admin-brand">
                    <p className="lux-subtitle">Royal Chair Admin</p>
                    <h1 className="admin-title">Booking Control Panel</h1>
                    <p className="admin-copy">
                        Manage salon data, schedules, phone bookings, and service availability from one place.
                    </p>
                </div>

                <div className="admin-header-actions">
                    <AdminColorSchemeToggle />
                    <button type="button" className="btn-gold admin-toolbar-btn" onClick={onRefresh} disabled={loading}>
                        <RefreshCw size={16} />
                        {loading ? 'Refreshing...' : 'Refresh'}
                    </button>
                    <button type="button" className="btn-gold admin-toolbar-btn" onClick={onLogout}>
                        <LogOut size={16} />
                        Logout
                    </button>
                </div>
            </header>

            <nav className="admin-nav glass-card gold-border" aria-label="Admin sections">
                {NAV_ITEMS.map((item) => {
                    const Icon = item.icon;
                    return (
                        <NavLink
                            key={item.to}
                            to={item.to}
                            className={({ isActive }) => `admin-nav-link ${isActive ? 'admin-nav-link-active' : ''}`}
                        >
                            <Icon size={16} />
                            <span>{item.label}</span>
                        </NavLink>
                    );
                })}
            </nav>

            {globalError ? <p className="section-error">{globalError}</p> : null}
            {globalSuccess ? <p className="section-ok">{globalSuccess}</p> : null}

            <div className="admin-route-shell">{children}</div>
        </main>
    );
}
