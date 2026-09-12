import { beforeEach, describe, expect, it, vi } from 'vitest';

const axiosState = vi.hoisted(() => ({
    api: null,
    requestInterceptor: null,
    responseRejected: null,
    calls: []
}));

vi.mock('axios', () => ({
    default: {
        create: vi.fn(() => axiosState.api)
    }
}));

function createMockApi() {
    const applyRequestInterceptor = (config) => {
        const nextConfig = axiosState.requestInterceptor
            ? axiosState.requestInterceptor(config)
            : config;
        axiosState.calls.push(nextConfig);
        return nextConfig;
    };

    return {
        interceptors: {
            request: {
                use: vi.fn((handler) => {
                    axiosState.requestInterceptor = handler;
                })
            },
            response: {
                use: vi.fn((_, rejected) => {
                    axiosState.responseRejected = rejected;
                })
            }
        },
        get: vi.fn(async (url, config = {}) => {
            applyRequestInterceptor({ ...config, url, method: 'get' });
            return {
                data: {
                    username: 'admin',
                    expiresInSeconds: 3600,
                    csrfToken: 'session-csrf-token'
                }
            };
        }),
        post: vi.fn(async (url, data, config = {}) => {
            applyRequestInterceptor({ ...config, url, method: 'post' });
            if (url.includes('/api/v1/public/auth/login')) {
                return {
                    data: {
                        username: 'admin',
                        expiresInSeconds: 3600,
                        csrfToken: 'login-csrf-token'
                    }
                };
            }

            return { data: { id: 'created' } };
        }),
        put: vi.fn(async (url, data, config = {}) => {
            applyRequestInterceptor({ ...config, url, method: 'put' });
            return { data: { id: 'updated' } };
        }),
        delete: vi.fn(async (url, config = {}) => {
            applyRequestInterceptor({ ...config, url, method: 'delete' });
            return { data: {} };
        })
    };
}

async function loadAdminApi() {
    vi.resetModules();
    axiosState.api = createMockApi();
    axiosState.requestInterceptor = null;
    axiosState.responseRejected = null;
    axiosState.calls = [];
    return import('../admin/api');
}

describe('admin API CSRF handling', () => {
    beforeEach(() => {
        vi.unstubAllGlobals();
    });

    it('stores the login CSRF token in memory and sends it with unsafe admin requests', async () => {
        const { adminApi, authApi } = await loadAdminApi();

        await authApi.login({ username: 'admin', password: 'password' });
        await adminApi.createEmployee({ name: 'Ada' });

        expect(axiosState.calls.at(-1).headers).toMatchObject({
            'X-Admin-CSRF': 'login-csrf-token'
        });
    });

    it('refreshes the in-memory CSRF token from session checks', async () => {
        const { adminApi, authApi } = await loadAdminApi();

        await authApi.getSession();
        await adminApi.updateHairSalon({ name: 'Royal Chair' });

        expect(axiosState.calls.at(-1).headers).toMatchObject({
            'X-Admin-CSRF': 'session-csrf-token'
        });
    });

    it('sends the in-memory CSRF token on keepalive admin cleanup requests', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(null, { status: 204 }))));
        const { adminApi, authApi } = await loadAdminApi();

        await authApi.login({ username: 'admin', password: 'password' });
        await adminApi.releaseAdminBookingHoldKeepalive('booking-1', 'hold-session-1');

        expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/api/v1/admin/bookings/hold/booking-1'), {
            method: 'DELETE',
            keepalive: true,
            credentials: 'include',
            headers: {
                'X-Admin-CSRF': 'login-csrf-token',
                'X-Admin-Hold-Session-Id': 'hold-session-1'
            }
        });
    });
});
