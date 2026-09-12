import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    publicApi,
    PUBLIC_FEATURE_UNAVAILABLE_MESSAGE,
    isPublicFeatureUnavailableError
} from '../api/publicApi';

function textResponse(body, status, contentType = 'text/plain') {
    return new Response(body, {
        status,
        headers: {
            'Content-Type': contentType
        }
    });
}

function jsonResponse(body, status) {
    return textResponse(JSON.stringify(body), status, 'application/json');
}

describe('publicApi public error masking', () => {
    beforeEach(() => {
        vi.stubGlobal('fetch', vi.fn());
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('masks network failures as the public outage message', async () => {
        fetch.mockRejectedValueOnce(new TypeError('Failed to fetch'));

        await expect(publicApi.getTreatments()).rejects.toMatchObject({
            message: PUBLIC_FEATURE_UNAVAILABLE_MESSAGE,
            status: 0,
            isFeatureUnavailable: true
        });
    });

    it('masks Spring plain-text CORS failures as the public outage message', async () => {
        fetch.mockResolvedValueOnce(textResponse('Invalid CORS request', 403));

        await expect(publicApi.holdBookingSlot({
            employeeId: 'employee-1',
            treatmentId: 'treatment-1',
            bookingDate: '2026-05-07',
            startTime: '10:00:00',
            endTime: '10:30:00'
        })).rejects.toMatchObject({
            message: PUBLIC_FEATURE_UNAVAILABLE_MESSAGE,
            status: 403,
            payload: 'Invalid CORS request',
            isFeatureUnavailable: true
        });
    });

    it('masks public forbidden responses as the public outage message', async () => {
        fetch.mockResolvedValueOnce(jsonResponse({ error: 'Forbidden', message: 'Forbidden' }, 403));

        await expect(publicApi.getAvailability({
            employeeId: 'employee-1',
            treatmentId: 'treatment-1',
            date: '2026-05-07'
        })).rejects.toMatchObject({
            message: PUBLIC_FEATURE_UNAVAILABLE_MESSAGE,
            status: 403,
            isFeatureUnavailable: true
        });
    });

    it('keeps actionable booking validation messages visible', async () => {
        expect.assertions(2);

        const message = 'This slot has already been booked by someone else.';
        fetch.mockResolvedValueOnce(jsonResponse({ message }, 400));

        try {
            await publicApi.holdBookingSlot({
                employeeId: 'employee-1',
                treatmentId: 'treatment-1',
                bookingDate: '2026-05-07',
                startTime: '10:00:00',
                endTime: '10:30:00'
            });
        } catch (error) {
            expect(error.message).toBe(message);
            expect(isPublicFeatureUnavailableError(error)).toBe(false);
        }
    });

    it('sends the hold access token on protected public lifecycle calls', async () => {
        fetch.mockImplementation(() => Promise.resolve(jsonResponse({}, 200)));

        await publicApi.getBooking('booking-1', 'hold-token');
        await publicApi.validateHeldBookingCheckout('booking-1', { customer: {} }, 'hold-token');
        await publicApi.prepareHeldBookingCheckout('booking-1', { customer: {} }, 'hold-token');
        await publicApi.confirmHeldBooking('booking-1', { paymentIntentId: 'pi_123' }, 'hold-token');
        await publicApi.cancelBooking('booking-1', 'hold-token');

        expect(fetch).toHaveBeenCalledTimes(5);
        for (const [, options] of fetch.mock.calls) {
            expect(options.headers).toMatchObject({
                'X-Booking-Hold-Access-Token': 'hold-token'
            });
        }
    });
});
