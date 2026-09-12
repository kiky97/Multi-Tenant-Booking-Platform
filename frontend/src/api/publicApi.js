import { extractFriendlyErrorMessage } from '../utils/appErrorBus';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
const BOOKING_DEVICE_ID_STORAGE_KEY = 'bookingDeviceId';
const HOLD_ACCESS_TOKEN_HEADER = 'X-Booking-Hold-Access-Token';
export const PUBLIC_FEATURE_UNAVAILABLE_MESSAGE =
    'Out of Service. Please contact the administration for assistance.';

const TECHNICAL_PUBLIC_ERROR_PATTERNS = [
    /invalid cors request/i,
    /cors policy/i,
    /access-control-allow-origin/i,
    /failed to fetch/i,
    /load failed/i,
    /networkerror/i,
    /unexpected error occurred/i,
    /whitelabel error page/i
];

function buildRequestUrl(path, params) {
    const url = new URL(`${API_BASE_URL}${path}`, window.location.origin);

    if (params) {
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '') {
                url.searchParams.set(key, value);
            }
        });
    }

    return url;
}

export class PublicApiError extends Error {
    constructor(message, status, payload, options = {}) {
        super(message);
        this.name = 'PublicApiError';
        this.status = status;
        this.payload = payload;
        this.fieldErrors = payload?.fieldErrors || {};
        this.isFeatureUnavailable = Boolean(options.isFeatureUnavailable);

        if (options.cause) {
            this.cause = options.cause;
        }
    }
}

function getPayloadText(payload) {
    if (!payload) {
        return '';
    }

    if (typeof payload === 'string') {
        return payload;
    }

    if (typeof payload === 'object') {
        return [payload.message, payload.error, payload.path]
            .filter(Boolean)
            .join(' ');
    }

    return String(payload);
}

function isTechnicalPublicFailure(status, payload, message = '') {
    if (status === 0 || (typeof status === 'number' && status >= 500)) {
        return true;
    }

    const searchableText = `${message} ${getPayloadText(payload)}`.trim();

    if (!searchableText) {
        return false;
    }

    if (TECHNICAL_PUBLIC_ERROR_PATTERNS.some((pattern) => pattern.test(searchableText))) {
        return true;
    }

    return status === 403 && /\bforbidden\b/i.test(searchableText);
}

export function isPublicFeatureUnavailableError(error) {
    return Boolean(
        error?.isFeatureUnavailable ||
        isTechnicalPublicFailure(error?.status ?? error?.response?.status, error?.payload, error?.message)
    );
}

function generateDeviceId() {
    if (typeof window !== 'undefined' && window.crypto?.randomUUID) {
        return window.crypto.randomUUID();
    }

    return `booking-device-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function getBookingDeviceId() {
    if (typeof window === 'undefined') {
        return null;
    }

    try {
        const existingDeviceId = window.localStorage.getItem(BOOKING_DEVICE_ID_STORAGE_KEY);
        if (existingDeviceId) {
            return existingDeviceId;
        }

        const nextDeviceId = generateDeviceId();
        window.localStorage.setItem(BOOKING_DEVICE_ID_STORAGE_KEY, nextDeviceId);
        return nextDeviceId;
    } catch {
        return null;
    }
}

function buildHoldAccessTokenHeaders(holdAccessToken) {
    return holdAccessToken
        ? { [HOLD_ACCESS_TOKEN_HEADER]: holdAccessToken }
        : undefined;
}

async function request(path, options = {}) {
    const { params, headers, body, ...rest } = options;
    const url = buildRequestUrl(path, params);

    let response;

    try {
        response = await fetch(url.toString(), {
            ...rest,
            headers: {
                Accept: 'application/json',
                ...(body ? { 'Content-Type': 'application/json' } : {}),
                ...headers
            },
            body
        });
    } catch (error) {
        throw new PublicApiError(
            PUBLIC_FEATURE_UNAVAILABLE_MESSAGE,
            0,
            null,
            {
                cause: error,
                isFeatureUnavailable: true
            }
        );
    }

    const isJson = response.headers.get('content-type')?.includes('application/json');
    const payload = isJson
        ? await response.json().catch(() => null)
        : await response.text().catch(() => '');

    if (!response.ok) {
        const message = extractFriendlyErrorMessage(
            {
                message: payload?.message || (typeof payload === 'string' ? payload : ''),
                payload,
                response: {
                    data: payload,
                    status: response.status
                },
                status: response.status
            },
            `Request failed with status ${response.status}`
        );

        const isFeatureUnavailable = isTechnicalPublicFailure(response.status, payload, message);

        throw new PublicApiError(
            isFeatureUnavailable ? PUBLIC_FEATURE_UNAVAILABLE_MESSAGE : message,
            response.status,
            payload,
            { isFeatureUnavailable }
        );
    }

    return payload;
}

export const publicApi = {
    getHairSalon: () => request('/api/v1/public/hair-salon'),
    getEmployees: (params) => request('/api/v1/public/employees', { params }),
    getTreatments: () => request('/api/v1/public/treatments'),
    getBooking: (bookingId, holdAccessToken) =>
        request(`/api/v1/public/bookings/${bookingId}`, {
            headers: buildHoldAccessTokenHeaders(holdAccessToken)
        }),
    getBookingCheckoutConfig: () => request('/api/v1/public/booking-checkout-config'),
    getAvailability: ({ employeeId, date, treatmentId }) =>
        request('/api/v1/public/availability', {
            params: { employeeId, date, treatmentId }
        }),
    holdBookingSlot: (payload) => {
        const bookingDeviceId = getBookingDeviceId();

        return request('/api/v1/public/bookings/hold', {
            method: 'POST',
            headers: bookingDeviceId
                ? { 'X-Booking-Device-Id': bookingDeviceId }
                : undefined,
            body: JSON.stringify(payload)
        });
    },
    validateHeldBookingCheckout: (bookingId, payload, holdAccessToken) =>
        request(`/api/v1/public/bookings/${bookingId}/checkout/validate`, {
            method: 'POST',
            headers: buildHoldAccessTokenHeaders(holdAccessToken),
            body: JSON.stringify(payload)
        }),
    prepareHeldBookingCheckout: (bookingId, payload, holdAccessToken) =>
        request(`/api/v1/public/bookings/${bookingId}/checkout`, {
            method: 'POST',
            headers: buildHoldAccessTokenHeaders(holdAccessToken),
            body: JSON.stringify(payload)
        }),
    confirmHeldBooking: (bookingId, payload, holdAccessToken) =>
        request(`/api/v1/public/bookings/${bookingId}/confirm`, {
            method: 'POST',
            headers: buildHoldAccessTokenHeaders(holdAccessToken),
            body: JSON.stringify(payload)
        }),
    cancelBooking: (bookingId, holdAccessToken) =>
        request(`/api/v1/public/bookings/${bookingId}`, {
            method: 'DELETE',
            headers: buildHoldAccessTokenHeaders(holdAccessToken)
        })
};
