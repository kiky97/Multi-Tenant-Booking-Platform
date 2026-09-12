import { MemoryRouter, useLocation } from 'react-router-dom';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import AdminApp from '../admin/App';
import { ColorSchemeProvider } from '../context/ColorSchemeContext';
import { publicApi } from '../api/publicApi';
import { authApi } from '../admin/api';
import {
    FORCE_HOME_TOP_STORAGE_KEY,
    markHomeTopScrollPending
} from '../utils/bookingSuccessNavigation';

const salon = {
    id: 'salon-1',
    name: 'Royal Chair',
    description: 'Premium grooming in Ennis.',
    email: 'hello@example.com',
    phone: '+353 83 182 3456',
    address: 'Royal Chair Barber & Beauty Salon, Ireland',
    workingHours: []
};

const treatment = {
    id: 'treatment-1',
    name: 'Royal Cut',
    description: 'Precision haircut and styling.',
    durationMinutes: 30,
    price: 25,
    displayOrder: 1
};

const employee = {
    id: 'employee-1',
    name: 'Alex Barber',
    role: 'Senior Barber',
    bio: 'Sharp detail and calm service.',
    bookable: true,
    treatmentIds: [treatment.id],
    displayOrder: 1
};

const availableSlot = {
    startTime: '10:00:00',
    endTime: '10:30:00',
    available: true,
    status: 'AVAILABLE'
};

vi.mock('../api/publicApi', () => {
    class PublicApiError extends Error {
        constructor(message, status, payload) {
            super(message);
            this.name = 'PublicApiError';
            this.status = status;
            this.payload = payload;
            this.fieldErrors = payload?.fieldErrors || {};
        }
    }

    return {
        PublicApiError,
        publicApi: {
            getHairSalon: vi.fn(),
            getEmployees: vi.fn(),
            getTreatments: vi.fn(),
            getBooking: vi.fn(),
            getBookingCheckoutConfig: vi.fn(),
            getAvailability: vi.fn(),
            holdBookingSlot: vi.fn(),
            validateHeldBookingCheckout: vi.fn(),
            prepareHeldBookingCheckout: vi.fn(),
            confirmHeldBooking: vi.fn(),
            cancelBooking: vi.fn()
        }
    };
});

vi.mock('../admin/api', () => ({
    authApi: {
        getSession: vi.fn(),
        login: vi.fn(),
        logout: vi.fn()
    },
    adminApi: {},
    publicApi: {},
    getApiErrorMessage: vi.fn(() => 'Request failed'),
    setUnauthorizedHandler: vi.fn(() => vi.fn())
}));

vi.mock('../components/booking/StripeHoldModal', () => ({
    default: ({ open, onAuthorize, onConfirm, onClose }) => (
        open ? (
            <div role="dialog" aria-label="Mock Stripe checkout">
                <button
                    type="button"
                    onClick={async () => {
                        const checkoutSession = await onAuthorize('ct_mock');
                        await onConfirm(checkoutSession?.paymentIntentId || 'pi_mock');
                    }}
                >
                    Complete mocked payment
                </button>
                <button type="button" onClick={onClose}>Cancel mocked payment</button>
            </div>
        ) : null
    )
}));

function LocationProbe() {
    const location = useLocation();
    return <span data-testid="current-path">{location.pathname}</span>;
}

function renderPublicApp(initialPath = '/') {
    return render(
        <MemoryRouter initialEntries={[initialPath]}>
            <ColorSchemeProvider>
                <App />
                <LocationProbe />
            </ColorSchemeProvider>
        </MemoryRouter>
    );
}

function renderAdminApp(initialPath = '/bookings') {
    return render(
        <MemoryRouter initialEntries={[initialPath]}>
            <ColorSchemeProvider>
                <AdminApp />
            </ColorSchemeProvider>
        </MemoryRouter>
    );
}

function mockPublicApiDefaults() {
    publicApi.getHairSalon.mockResolvedValue(salon);
    publicApi.getEmployees.mockImplementation(() => Promise.resolve([employee]));
    publicApi.getTreatments.mockResolvedValue([treatment]);
    publicApi.getBookingCheckoutConfig.mockResolvedValue({
        currency: 'eur',
        stripePublishableKey: 'pk_test_mock'
    });
    publicApi.getAvailability.mockResolvedValue([availableSlot]);
    publicApi.holdBookingSlot.mockResolvedValue({
        id: 'hold-1',
        expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
        holdAccessToken: 'hold-token'
    });
    publicApi.validateHeldBookingCheckout.mockResolvedValue({});
    publicApi.prepareHeldBookingCheckout.mockResolvedValue({
        paymentIntentId: 'pi_mock',
        paymentStatus: 'succeeded'
    });
    publicApi.confirmHeldBooking.mockResolvedValue({
        id: 'booking-1',
        status: 'CONFIRMED',
        bookingDate: '2026-05-04',
        startTime: availableSlot.startTime,
        endTime: availableSlot.endTime,
        customerName: 'Alice Client',
        customerEmail: 'alice@example.com'
    });
    publicApi.getBooking.mockResolvedValue({
        id: 'booking-1',
        status: 'CONFIRMED',
        bookingDate: '2026-05-04',
        startTime: availableSlot.startTime,
        endTime: availableSlot.endTime
    });
    publicApi.cancelBooking.mockResolvedValue({});
}

describe('production smoke coverage', () => {
    beforeEach(() => {
        mockPublicApiDefaults();
        authApi.getSession.mockRejectedValue({ response: { status: 401 } });
    });

    it('renders the public home page and expected navbar links', async () => {
        renderPublicApp('/');

        expect(await screen.findByRole('heading', { name: /your style, perfected/i })).toBeInTheDocument();
        expect(screen.getAllByRole('link', { name: /home/i }).length).toBeGreaterThan(0);
        expect(screen.getAllByRole('link', { name: /services/i }).length).toBeGreaterThan(0);
        expect(screen.getAllByRole('link', { name: /book now/i }).length).toBeGreaterThan(0);
        expect(screen.getAllByRole('link', { name: /faq/i }).length).toBeGreaterThan(0);
        expect(screen.getAllByRole('link', { name: /contact/i }).length).toBeGreaterThan(0);
    });

    it('keeps section scrolling wired without hiding the scrollbar', async () => {
        const user = userEvent.setup();
        renderPublicApp('/');

        await screen.findByRole('heading', { name: /your style, perfected/i });
        await user.click(screen.getAllByRole('link', { name: /contact/i })[0]);

        await waitFor(() => {
            expect(window.scrollTo).toHaveBeenCalled();
        });
        expect(document.documentElement).not.toHaveClass('auto-scroll-hidden');
    });

    it('renders the booking page basic flow shell', async () => {
        renderPublicApp('/booking');

        await waitFor(() => {
            expect(screen.getByRole('heading', { name: /royal chair booking/i })).toBeInTheDocument();
        }, { timeout: 5000 });
        await waitFor(() => {
            expect(screen.getByRole('heading', { name: /choose your service/i })).toBeInTheDocument();
        }, { timeout: 5000 });
        await waitFor(() => {
            expect(screen.getByText('Royal Cut')).toBeInTheDocument();
        }, { timeout: 5000 });
    });

    it('honors the booking success close contract by scrolling home to top', async () => {
        markHomeTopScrollPending();
        expect(window.sessionStorage.getItem(FORCE_HOME_TOP_STORAGE_KEY)).toBe('1');

        renderPublicApp('/');

        expect(await screen.findByRole('heading', { name: /your style, perfected/i })).toBeInTheDocument();
        await waitFor(() => {
            expect(window.scrollTo).toHaveBeenCalledWith(
                expect.objectContaining({ top: 0, left: 0, behavior: 'auto' })
            );
        });
        expect(window.sessionStorage.getItem(FORCE_HOME_TOP_STORAGE_KEY)).toBeNull();
    });

    it('renders the admin auth guard login screen when no session is present', async () => {
        renderAdminApp('/bookings');

        expect(await screen.findByRole('heading', { name: /admin access/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /login/i })).toBeInTheDocument();
    });
});
