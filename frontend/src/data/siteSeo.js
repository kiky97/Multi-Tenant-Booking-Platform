const configuredSiteUrl = import.meta.env?.VITE_PUBLIC_SITE_URL || 'https://salon-booking-platform-frontend.onrender.com';

export const siteSeo = {
    fallbackBusinessName: 'Royal Chair Barber & Beauty Salon',
    defaultTitle: 'Royal Chair Barber & Beauty Salon | Ennis Haircuts & Beard Trims',
    defaultDescription:
        'Book haircuts, beard trims, barber shop grooming, and beauty salon services in Ennis.',
    siteUrl: configuredSiteUrl,
    defaultImage: '/logo-royal.jpg',
    bookingPath: '/booking',
    localSeoKeywords: [
        'hair salon Ennis',
        'barber Ennis',
        'haircuts Ennis',
        'beard trims Ennis',
        'beauty salon Ennis',
        'Royal Chair Ennis'
    ]
};

const schemaDayNames = {
    MONDAY: 'Monday',
    TUESDAY: 'Tuesday',
    WEDNESDAY: 'Wednesday',
    THURSDAY: 'Thursday',
    FRIDAY: 'Friday',
    SATURDAY: 'Saturday',
    SUNDAY: 'Sunday'
};

const dayOrder = Object.keys(schemaDayNames);
const schemaTimePattern = /^([01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/;

export function cleanText(value) {
    return typeof value === 'string' ? value.trim() : '';
}

function stripTrailingSlash(value) {
    return cleanText(value).replace(/\/$/, '');
}

function isHttpUrl(value) {
    return /^https?:\/\//i.test(cleanText(value));
}

function isPublicAssetReference(value) {
    const trimmedValue = cleanText(value);

    return isHttpUrl(trimmedValue) || trimmedValue.startsWith('//') || trimmedValue.startsWith('/');
}

function hasSeoValue(value) {
    if (value == null) {
        return false;
    }

    if (typeof value === 'string') {
        return value.trim().length > 0;
    }

    if (Array.isArray(value)) {
        return value.length > 0;
    }

    if (typeof value === 'object') {
        return Object.keys(value).length > 0;
    }

    return true;
}

function assignIfPresent(target, key, value) {
    if (hasSeoValue(value)) {
        target[key] = value;
    }
}

function runtimeOrigin() {
    if (typeof window === 'undefined' || !window.location?.origin) {
        return '';
    }

    return window.location.origin;
}

export function getBusinessName(salonInfo) {
    return cleanText(salonInfo?.name) || siteSeo.fallbackBusinessName;
}

export function getShortBusinessName(salonInfo) {
    const name = getBusinessName(salonInfo);

    if (name.length <= 24) {
        return name;
    }

    return name.split(/\s+/).slice(0, 2).join(' ') || name;
}

export function getSiteUrl(_salonInfo) {
    if (isHttpUrl(siteSeo.siteUrl)) {
        return stripTrailingSlash(siteSeo.siteUrl);
    }

    return stripTrailingSlash(runtimeOrigin());
}

export function resolvePublicUrl(value, salonInfo) {
    const trimmedValue = cleanText(value);

    if (!trimmedValue) {
        return '';
    }

    if (isHttpUrl(trimmedValue)) {
        return trimmedValue;
    }

    if (trimmedValue.startsWith('//')) {
        return `https:${trimmedValue}`;
    }

    const baseUrl = getSiteUrl(salonInfo);

    if (!baseUrl) {
        return trimmedValue;
    }

    const normalizedPath = trimmedValue.startsWith('/') ? trimmedValue : `/${trimmedValue}`;

    return `${baseUrl}${normalizedPath}`;
}

export function buildCanonicalUrl(path = '/', salonInfo) {
    const trimmedPath = cleanText(path) || '/';

    if (isHttpUrl(trimmedPath)) {
        return trimmedPath;
    }

    return resolvePublicUrl(trimmedPath, salonInfo);
}

export function getPublicLogoPath(salonInfo, fallbackLogo = '') {
    const publicLogoPath = cleanText(fallbackLogo || siteSeo.defaultImage);

    return isPublicAssetReference(publicLogoPath) ? publicLogoPath : fallbackLogo;
}

export function getBookingPageUrl(salonInfo, { includeFallback = true } = {}) {
    return includeFallback ? buildCanonicalUrl(siteSeo.bookingPath, salonInfo) : '';
}

export function formatSalonAddress(salonInfo) {
    return cleanText(salonInfo?.address);
}

export function buildGoogleMapsUrl(salonInfo, mode = 'search') {
    const destination = [
        cleanText(salonInfo?.name),
        formatSalonAddress(salonInfo)
    ]
        .filter(Boolean)
        .join(', ') || 'barber shop, Ennis, Ireland';
    const encodedDestination = encodeURIComponent(destination);

    if (mode === 'directions') {
        return `https://www.google.com/maps/dir/?api=1&destination=${encodedDestination}&travelmode=driving`;
    }

    return `https://www.google.com/maps/search/?api=1&query=${encodedDestination}`;
}

function buildOpeningHoursSpecification(workingHours) {
    if (!Array.isArray(workingHours)) {
        return [];
    }

    const dayIndex = (dayOfWeek) => {
        const index = dayOrder.indexOf(dayOfWeek);
        return index === -1 ? Number.MAX_SAFE_INTEGER : index;
    };

    return [...workingHours]
        .sort((a, b) => dayIndex(a?.dayOfWeek) - dayIndex(b?.dayOfWeek))
        .filter((item) => (
            item?.workingDay
            && schemaDayNames[item.dayOfWeek]
            && schemaTimePattern.test(String(item.openTime))
            && schemaTimePattern.test(String(item.closeTime))
        ))
        .map((item) => ({
            '@type': 'OpeningHoursSpecification',
            dayOfWeek: schemaDayNames[item.dayOfWeek],
            opens: String(item.openTime).slice(0, 5),
            closes: String(item.closeTime).slice(0, 5)
        }));
}

export function buildLocalBusinessStructuredData(salonInfo) {
    const name = getBusinessName(salonInfo);

    if (!name) {
        return null;
    }

    const description = cleanText(salonInfo?.description);
    const telephone = cleanText(salonInfo?.phone);
    const email = cleanText(salonInfo?.email);
    const canonicalHome = buildCanonicalUrl('/', salonInfo);
    const resolvedLogoImage = resolvePublicUrl(getPublicLogoPath(salonInfo, siteSeo.defaultImage), salonInfo);
    const resolvedBookingPage = getBookingPageUrl(salonInfo);
    const addressText = formatSalonAddress(salonInfo);
    const openingHoursSpecification = buildOpeningHoursSpecification(salonInfo?.workingHours);
    const structuredData = {
        '@context': 'https://schema.org',
        '@type': 'HairSalon',
        '@id': `${stripTrailingSlash(canonicalHome)}#localbusiness`,
        name
    };

    assignIfPresent(structuredData, 'url', canonicalHome);
    assignIfPresent(structuredData, 'description', description);
    assignIfPresent(structuredData, 'telephone', telephone);
    assignIfPresent(structuredData, 'email', email);
    assignIfPresent(structuredData, 'logo', resolvedLogoImage);
    assignIfPresent(structuredData, 'image', resolvedLogoImage);
    assignIfPresent(structuredData, 'address', addressText);
    assignIfPresent(structuredData, 'openingHoursSpecification', openingHoursSpecification);

    if (resolvedBookingPage) {
        structuredData.potentialAction = {
            '@type': 'ReserveAction',
            name: 'Book an appointment',
            target: resolvedBookingPage
        };
    }

    return structuredData;
}

function pageTitleWithBusinessName(salonInfo, suffix) {
    return `${getBusinessName(salonInfo)} | ${suffix}`;
}

export const pageSeo = {
    home: {
        title: (salonInfo) => pageTitleWithBusinessName(salonInfo, 'Ennis Haircuts & Beard Trims'),
        description: (salonInfo) =>
            `Visit ${getBusinessName(salonInfo)} in Ennis for barber shop haircuts, beard trims, beauty salon styling, grooming, and easy online booking.`,
        canonical: '/',
        robots: 'index,follow',
        includeLocalBusinessStructuredData: true
    },
    services: {
        title: 'Services | Ennis Haircuts, Beard Trims & Beauty Salon',
        description: (salonInfo) =>
            `Explore services from ${getBusinessName(salonInfo)} in Ennis, including barber shop haircuts, beard trims, grooming, and beauty salon treatments.`,
        canonical: '/services',
        robots: 'index,follow',
        includeLocalBusinessStructuredData: true
    },
    booking: {
        title: (salonInfo) => `Book Online | ${getBusinessName(salonInfo)} Ennis`,
        description: (salonInfo) =>
            `Book appointments online with ${getBusinessName(salonInfo)} in Ennis for haircuts, beard trims, barber shop grooming, and beauty salon services.`,
        canonical: '/booking',
        robots: 'index,follow',
        includeLocalBusinessStructuredData: true
    },
    faq: {
        title: (salonInfo) => `FAQ | ${getBusinessName(salonInfo)} Ennis`,
        description: (salonInfo) =>
            `Find answers about ${getBusinessName(salonInfo)} in Ennis, including barber shop bookings, haircuts, beard trims, beauty salon services, and visits.`,
        canonical: '/faq',
        robots: 'index,follow',
        includeLocalBusinessStructuredData: true
    }
};
