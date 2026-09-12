import { describe, expect, it } from 'vitest';
import {
    buildCanonicalUrl,
    buildGoogleMapsUrl,
    buildLocalBusinessStructuredData,
    pageSeo,
    siteSeo
} from './siteSeo';

function collectValues(value, values = []) {
    values.push(value);

    if (Array.isArray(value)) {
        value.forEach((item) => collectValues(item, values));
        return values;
    }

    if (value && typeof value === 'object') {
        Object.values(value).forEach((item) => collectValues(item, values));
    }

    return values;
}

describe('siteSeo helpers', () => {
    it('keeps public route titles, descriptions, and canonicals unique', () => {
        const salonInfo = { name: 'Backend Salon' };
        const routeConfigs = [pageSeo.home, pageSeo.services, pageSeo.booking, pageSeo.faq];
        const resolveValue = (value) => (typeof value === 'function' ? value(salonInfo) : value);

        expect(new Set(routeConfigs.map((config) => resolveValue(config.title))).size).toBe(routeConfigs.length);
        expect(new Set(routeConfigs.map((config) => resolveValue(config.description))).size).toBe(routeConfigs.length);
        expect(new Set(routeConfigs.map((config) => config.canonical)).size).toBe(routeConfigs.length);
    });

    it('builds canonical URLs from the configured production domain', () => {
        expect(buildCanonicalUrl('/services')).toBe(`${siteSeo.siteUrl}/services`);
        expect(buildCanonicalUrl('booking')).toBe(`${siteSeo.siteUrl}/booking`);
        expect(buildCanonicalUrl('https://example.com/faq')).toBe('https://example.com/faq');
    });

    it('uses the existing plain salon address for maps and LocalBusiness data', () => {
        const salonInfo = {
            name: 'Backend Salon',
            address: 'Main Street, Ennis'
        };
        const structuredData = buildLocalBusinessStructuredData(salonInfo);

        expect(buildGoogleMapsUrl(salonInfo)).toContain('Backend%20Salon%2C%20Main%20Street%2C%20Ennis');
        expect(buildGoogleMapsUrl(salonInfo)).not.toContain('0%2C0');
        expect(structuredData.address).toBe('Main Street, Ennis');
        expect(typeof structuredData.address).toBe('string');
        expect(structuredData).not.toHaveProperty('geo');
    });

    it('uses only existing backend salon fields in HairSalon structured data', () => {
        const structuredData = buildLocalBusinessStructuredData({
            name: 'Backend Salon',
            description: 'Backend description',
            email: 'hello@example.com',
            phone: '+353 83 000 0000',
            address: 'Main Street, Ennis',
            workingHours: [
                {
                    dayOfWeek: 'MONDAY',
                    workingDay: true,
                    openTime: '09:30:00',
                    closeTime: '18:00:00'
                }
            ]
        });

        expect(structuredData).toMatchObject({
            '@type': 'HairSalon',
            name: 'Backend Salon',
            description: 'Backend description',
            email: 'hello@example.com',
            telephone: '+353 83 000 0000',
            address: 'Main Street, Ennis'
        });
        expect(structuredData.openingHoursSpecification).toEqual([
            {
                '@type': 'OpeningHoursSpecification',
                dayOfWeek: 'Monday',
                opens: '09:30',
                closes: '18:00'
            }
        ]);
    });

    it('omits unavailable LocalBusiness fields cleanly', () => {
        const structuredData = buildLocalBusinessStructuredData({
            name: 'Backend Salon',
            description: '   ',
            email: '',
            phone: null,
            address: '',
            workingHours: [
                {
                    dayOfWeek: 'TUESDAY',
                    workingDay: false,
                    openTime: '09:30:00',
                    closeTime: '18:00:00'
                }
            ]
        });

        expect(structuredData).not.toHaveProperty('description');
        expect(structuredData).not.toHaveProperty('email');
        expect(structuredData).not.toHaveProperty('telephone');
        expect(structuredData).not.toHaveProperty('address');
        expect(structuredData).not.toHaveProperty('openingHoursSpecification');
    });

    it('omits invalid opening hours from LocalBusiness JSON-LD', () => {
        const structuredData = buildLocalBusinessStructuredData({
            name: 'Backend Salon',
            workingHours: [
                {
                    dayOfWeek: 'NOT_A_DAY',
                    workingDay: true,
                    openTime: '09:30:00',
                    closeTime: '18:00:00'
                },
                {
                    dayOfWeek: 'MONDAY',
                    workingDay: true,
                    openTime: '9',
                    closeTime: '18:00:00'
                }
            ]
        });

        expect(structuredData).not.toHaveProperty('openingHoursSpecification');
    });

    it('does not output null, undefined, or empty strings in LocalBusiness JSON-LD', () => {
        const structuredData = buildLocalBusinessStructuredData({
            name: 'Backend Salon',
            email: 'contact@example.com'
        });

        collectValues(structuredData).forEach((value) => {
            expect(value).not.toBeNull();
            expect(value).not.toBeUndefined();

            if (typeof value === 'string') {
                expect(value.trim()).not.toBe('');
            }
        });
    });

    it('uses the configured public logo fallback instead of backend logo fields', () => {
        const structuredData = buildLocalBusinessStructuredData({
            name: 'Backend Salon'
        });

        expect(structuredData.logo).toContain('/logo-royal.jpg');
        expect(structuredData.image).toContain('/logo-royal.jpg');
    });
});
