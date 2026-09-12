import GoldButton from '../../components/ui/GoldButton';
import SectionTitle from '../../components/ui/SectionTitle';
import AdminColorSchemeToggle from '../components/AdminColorSchemeToggle';

export default function AdminLoginPage({
    loginForm,
    setLoginForm,
    clearFieldError,
    fieldErrors,
    sectionErrors,
    sectionSuccess,
    loading,
    onSubmit
}) {
    return (
        <main className="page admin-login-page">
            <div className="admin-login-toolbar">
                <AdminColorSchemeToggle />
            </div>

            <section className="panel panel-narrow admin-login-card">
                <SectionTitle title="Admin Access" subtitle="Royal Chair Control" />
                <p className="panel-note admin-login-copy">
                    Sign in to manage employees, treatments, salon schedule, and free phone bookings.
                </p>

                <form onSubmit={onSubmit} className="grid">
                    <label>
                        Username
                        <input
                            className="payment-input"
                            name="username"
                            autoComplete="username"
                            value={loginForm.username}
                            onChange={(event) => {
                                setLoginForm((current) => ({ ...current, username: event.target.value }));
                                clearFieldError('login', 'username');
                            }}
                        />
                        {fieldErrors.login?.username ? <span className="field-error">{fieldErrors.login.username}</span> : null}
                    </label>

                    <label>
                        Password
                        <input
                            className="payment-input"
                            name="current-password"
                            type="password"
                            autoComplete="current-password"
                            value={loginForm.password}
                            onChange={(event) => {
                                setLoginForm((current) => ({ ...current, password: event.target.value }));
                                clearFieldError('login', 'password');
                            }}
                        />
                        {fieldErrors.login?.password ? <span className="field-error">{fieldErrors.login.password}</span> : null}
                    </label>

                    <GoldButton type="submit" disabled={loading}>
                        {loading ? 'Signing in...' : 'Login'}
                    </GoldButton>
                </form>

                {sectionErrors.login ? <p className="section-error">{sectionErrors.login}</p> : null}
                {sectionSuccess.login ? <p className="section-ok">{sectionSuccess.login}</p> : null}
            </section>
        </main>
    );
}
