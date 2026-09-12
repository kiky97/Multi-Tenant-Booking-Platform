import { StrictMode, Suspense, lazy } from 'react';
import { createRoot } from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import { ColorSchemeProvider } from '../context/ColorSchemeContext';
import './index.css';
import '../styles/booking-shared.css';
import '../styles/booking.css';
import './styles.css';
import { primeThemeLogoAssets } from '../utils/themeAssets';

const AdminApp = lazy(() => import('./App'));
const GlobalErrorBoundary = lazy(() => import('../components/GlobalErrorBoundary'));

function resetPublicShellSideEffects() {
    const root = document.documentElement;
    const { body } = document;

    root.dataset.adminApp = 'true';
    body.dataset.adminApp = 'true';

    ['body-scroll-locked', 'auto-scroll-hidden', 'section-scroll-pending'].forEach((className) => {
        root.classList.remove(className);
        body.classList.remove(className);
    });

    body.style.position = '';
    body.style.top = '';
    body.style.left = '';
    body.style.right = '';
    body.style.width = '';
    body.style.overflow = '';
    body.style.overflowX = '';
    body.style.overflowY = '';
    body.style.paddingRight = '';
}

resetPublicShellSideEffects();
primeThemeLogoAssets();

const shellLockObserver = new MutationObserver(() => {
    resetPublicShellSideEffects();
});

shellLockObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class', 'style']
});

shellLockObserver.observe(document.body, {
    attributes: true,
    attributeFilter: ['class', 'style']
});

window.addEventListener('pageshow', resetPublicShellSideEffects);
window.addEventListener('focus', resetPublicShellSideEffects);
document.addEventListener('visibilitychange', () => {
    if (!document.hidden) {
        resetPublicShellSideEffects();
    }
});

const rootElement = document.getElementById('root');
const root = createRoot(rootElement);

root.render(
    <StrictMode>
        <Suspense fallback={null}>
            <GlobalErrorBoundary>
                <ColorSchemeProvider>
                    <HashRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
                        <AdminApp />
                    </HashRouter>
                </ColorSchemeProvider>
            </GlobalErrorBoundary>
        </Suspense>
    </StrictMode>
);
