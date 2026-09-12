import axios from 'axios';
import { extractFriendlyErrorMessage, reportAppError } from '../utils/appErrorBus';

const ADMIN_HOLD_SESSION_HEADER = 'X-Admin-Hold-Session-Id';
const ADMIN_CSRF_HEADER = 'X-Admin-CSRF';
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
let unauthorizedHandler = null;
let unauthorizedEventSent = false;
let adminCsrfToken = null;

const api = axios.create({
    baseURL: API_BASE_URL,
    timeout: 15000,
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json'
    }
});

function isUnsafeMethod(method = 'GET') {
    return !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(String(method).toUpperCase());
}

function isAdminApiRequest(url = '') {
    return String(url).includes('/api/v1/admin/');
}

function setHeader(headers, name, value) {
    if (typeof headers?.set === 'function') {
        headers.set(name, value);
        return headers;
    }

    return {
        ...(headers || {}),
        [name]: value
    };
}

api.interceptors.request.use((config) => {
    if (isUnsafeMethod(config.method) && isAdminApiRequest(config.url) && adminCsrfToken) {
        config.headers = setHeader(config.headers, ADMIN_CSRF_HEADER, adminCsrfToken);
    }

    return config;
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
        const status = error?.response?.status;
        const requestUrl = String(error?.config?.url || '');
        const isLoginRequest = requestUrl.includes('/api/v1/public/auth/login');
        const skipUnauthorizedHandler = Boolean(error?.config?.skipUnauthorizedHandler);

        if (status === 401 && !isLoginRequest && !skipUnauthorizedHandler) {
            clearAdminCsrfToken();
            if (!unauthorizedEventSent) {
                unauthorizedEventSent = true;
                unauthorizedHandler?.(error);
            }
        }

        return Promise.reject(error);
    }
);

function updateAdminCsrfToken(session) {
    adminCsrfToken = typeof session?.csrfToken === 'string' && session.csrfToken.trim()
        ? session.csrfToken
        : null;
}

function clearAdminCsrfToken() {
    adminCsrfToken = null;
}

export const authApi = {
    login: async (payload) => {
        unauthorizedEventSent = false;
        const session = (await api.post('/api/v1/public/auth/login', payload)).data;
        updateAdminCsrfToken(session);
        return session;
    },
    getSession: async () => {
        const session = (
            await api.get('/api/v1/admin/auth/session', {
                skipUnauthorizedHandler: true
            })
        ).data;

        if (!session || typeof session !== 'object' || Array.isArray(session)) {
            throw new Error('Invalid admin session response.');
        }

        updateAdminCsrfToken(session);
        return session;
    },
    logout: async () => {
        unauthorizedEventSent = false;
        const response = (
            await api.post('/api/v1/admin/auth/logout', null, {
                skipUnauthorizedHandler: true
            })
        ).data;
        clearAdminCsrfToken();
        return response;
    }
};

function buildAdminHoldHeaders(adminHoldSessionId) {
    return adminHoldSessionId
        ? {
            [ADMIN_HOLD_SESSION_HEADER]: adminHoldSessionId
        }
        : {};
}

function resolveApiUrl(path) {
    return API_BASE_URL ? `${API_BASE_URL}${path}` : path;
}

export const publicApi = {
    getHairSalon: async () => (await api.get('/api/v1/public/hair-salon')).data,
    getEmployees: async (params) =>
        (
            await api.get('/api/v1/public/employees', {
                params
            })
        ).data,
    getTreatments: async () => (await api.get('/api/v1/public/treatments')).data,
    getAvailability: async ({ employeeId, date, treatmentId }) =>
        (
            await api.get('/api/v1/public/availability', {
                params: { employeeId, date, treatmentId }
            })
        ).data
};

export const adminApi = {
    updateHairSalon: async (payload) => api.put('/api/v1/admin/hair-salon', payload),

    getHairSalonHours: async (hairSalonId) =>
        (await api.get(`/api/v1/admin/hair-salons/${hairSalonId}/hours`)).data,
    updateHairSalonHours: async (hairSalonId, dayOfWeek, payload) =>
        api.put(`/api/v1/admin/hair-salons/${hairSalonId}/hours/${dayOfWeek}`, payload),

    createEmployee: async (payload) => (await api.post('/api/v1/admin/employees', payload)).data,
    updateEmployee: async (id, payload) => api.put(`/api/v1/admin/employees/${id}`, payload),
    deleteEmployee: async (id) => api.delete(`/api/v1/admin/employees/${id}`),
    reorderEmployees: async (payload) => api.post('/api/v1/admin/employees/reorder', payload),

    getEmployeeSchedule: async (employeeId, from, to) =>
        (
            await api.get(`/api/v1/admin/employees/${employeeId}/schedule`, {
                params: { from, to }
            })
        ).data,
    getEmployeePeriodSettings: async () =>
        (await api.get('/api/v1/admin/employees/schedule/period')).data,
    upsertEmployeePeriod: async (payload) =>
        (await api.put('/api/v1/admin/employees/schedule/period', payload)).data,
    upsertEmployeeDay: async (employeeId, payload) =>
        api.put(`/api/v1/admin/employees/${employeeId}/schedule`, payload),

    createTreatment: async (payload) => (await api.post('/api/v1/admin/treatments', payload)).data,
    updateTreatment: async (id, payload) => api.put(`/api/v1/admin/treatments/${id}`, payload),
    deleteTreatment: async (id) => api.delete(`/api/v1/admin/treatments/${id}`),
    reorderTreatments: async (payload) => api.post('/api/v1/admin/treatments/reorder', payload),

    holdAdminBookingSlot: async (payload, adminHoldSessionId) =>
        (
            await api.post('/api/v1/admin/bookings/hold', payload, {
                headers: buildAdminHoldHeaders(adminHoldSessionId)
            })
        ).data,
    refreshAdminBookingHold: async (bookingId, adminHoldSessionId) =>
        (
            await api.post(`/api/v1/admin/bookings/${bookingId}/hold-refresh`, null, {
                headers: buildAdminHoldHeaders(adminHoldSessionId)
            })
        ).data,
    releaseAdminBookingHold: async (bookingId, adminHoldSessionId) =>
        api.delete(`/api/v1/admin/bookings/hold/${bookingId}`, {
            headers: buildAdminHoldHeaders(adminHoldSessionId)
        }),
    releaseAdminBookingHoldKeepalive: async (bookingId, adminHoldSessionId) => {
        if (!bookingId || !adminHoldSessionId || typeof fetch !== 'function') {
            return;
        }

        try {
            const csrfHeaders = adminCsrfToken
                ? {
                    [ADMIN_CSRF_HEADER]: adminCsrfToken
                }
                : {};
            await fetch(resolveApiUrl(`/api/v1/admin/bookings/hold/${bookingId}`), {
                method: 'DELETE',
                keepalive: true,
                credentials: 'include',
                headers: {
                    ...csrfHeaders,
                    ...buildAdminHoldHeaders(adminHoldSessionId)
                }
            });
        } catch (error) {
            reportAppError(error, {
                source: 'admin-release-hold-keepalive',
                level: 'warn',
                message: 'The admin booking hold could not be released during page exit.',
                notify: false
            });
        }
    },
    createAdminBooking: async (payload, adminHoldSessionId) =>
        (
            await api.post('/api/v1/admin/bookings', payload, {
                headers: buildAdminHoldHeaders(adminHoldSessionId)
            })
        ).data,
    getAdminBookings: async (search = '') =>
        (
            await api.get('/api/v1/admin/bookings', {
                params: { search }
            })
        ).data,
    lookupBookingCustomer: async (phone) => {
        const response = await api.get('/api/v1/admin/bookings/customer-lookup', {
            params: { phone },
            validateStatus: (status) => (status >= 200 && status < 300) || status === 204
        });
        return response.status === 204 ? null : response.data;
    },
    updateBooking: async (bookingId, payload) =>
        (await api.put(`/api/v1/admin/bookings/${bookingId}`, payload)).data,
    cancelBooking: async (bookingId) =>
        (await api.post(`/api/v1/admin/bookings/${bookingId}/cancel`)).data,
    getBookingBlacklist: async () =>
        (await api.get('/api/v1/admin/bookings/blacklist')).data,
    createBookingBlacklistEntry: async (payload) =>
        (await api.post('/api/v1/admin/bookings/blacklist', payload)).data,
    deleteBookingBlacklistEntry: async (id) =>
        api.delete(`/api/v1/admin/bookings/blacklist/${id}`)
};

export function setUnauthorizedHandler(handler) {
    unauthorizedHandler = typeof handler === 'function' ? handler : null;

    return () => {
        if (unauthorizedHandler === handler) {
            unauthorizedHandler = null;
        }
    };
}

export function getApiErrorMessage(error) {
    return extractFriendlyErrorMessage(error, 'Request failed');
}
