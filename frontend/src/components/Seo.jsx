import { useMemo } from 'react';
import { Helmet } from 'react-helmet-async';
import {
    buildCanonicalUrl,
    buildLocalBusinessStructuredData,
    cleanText,
    getBusinessName,
    getPublicLogoPath,
    resolvePublicUrl,
    siteSeo
} from '../data/siteSeo';
import useHairSalonInfo from '../hooks/useHairSalonInfo';

function safeJsonLd(data) {
    return JSON.stringify(data).replace(/</g, '\\u003c');
}

function resolveMetaValue(value, salonInfo, fallback = '') {
    if (typeof value === 'function') {
        return value(salonInfo);
    }

    return value || fallback;
}

export default function Seo({
    title = siteSeo.defaultTitle,
    description = siteSeo.defaultDescription,
    canonical = '/',
    robots = 'index,follow',
    openGraph = {},
    twitter = {},
    structuredData = [],
    includeLocalBusinessStructuredData = false
}) {
    const salonInfo = useHairSalonInfo();
    const resolvedTitle = resolveMetaValue(title, salonInfo, siteSeo.defaultTitle);
    const resolvedDescription = resolveMetaValue(description, salonInfo, siteSeo.defaultDescription);
    const canonicalValue = resolveMetaValue(canonical, salonInfo, '/');
    const canonicalUrl = buildCanonicalUrl(canonicalValue, salonInfo);
    const siteName = cleanText(salonInfo?.name) || getBusinessName(salonInfo);

    const ogTitle = resolveMetaValue(openGraph.title, salonInfo, resolvedTitle);
    const ogDescription = resolveMetaValue(openGraph.description, salonInfo, resolvedDescription);
    const ogType = openGraph.type || 'website';
    const ogUrl = resolvePublicUrl(resolveMetaValue(openGraph.url, salonInfo, canonicalUrl), salonInfo);
    const ogImage = resolvePublicUrl(
        resolveMetaValue(openGraph.image, salonInfo, getPublicLogoPath(salonInfo, siteSeo.defaultImage)),
        salonInfo
    );

    const twitterTitle = resolveMetaValue(twitter.title, salonInfo, resolvedTitle);
    const twitterDescription = resolveMetaValue(twitter.description, salonInfo, resolvedDescription);
    const twitterImage = resolvePublicUrl(resolveMetaValue(twitter.image, salonInfo, ogImage), salonInfo);
    const twitterCard = twitter.card || 'summary_large_image';

    const localBusinessStructuredData = useMemo(
        () => (
            includeLocalBusinessStructuredData
                ? buildLocalBusinessStructuredData(salonInfo)
                : null
        ),
        [includeLocalBusinessStructuredData, salonInfo]
    );

    const structuredDataItems = useMemo(
        () => (Array.isArray(structuredData) ? structuredData : [structuredData])
            .concat(localBusinessStructuredData)
            .filter(Boolean),
        [localBusinessStructuredData, structuredData]
    );

    return (
        <Helmet prioritizeSeoTags>
            <title>{resolvedTitle}</title>
            <meta name="description" content={resolvedDescription} />
            <meta name="robots" content={robots} />
            {canonicalUrl ? <link rel="canonical" href={canonicalUrl} /> : null}

            <meta property="og:site_name" content={siteName} />
            <meta property="og:title" content={ogTitle} />
            <meta property="og:description" content={ogDescription} />
            <meta property="og:type" content={ogType} />
            {ogUrl ? <meta property="og:url" content={ogUrl} /> : null}
            {ogImage ? <meta property="og:image" content={ogImage} /> : null}

            <meta name="twitter:card" content={twitterCard} />
            <meta name="twitter:title" content={twitterTitle} />
            <meta name="twitter:description" content={twitterDescription} />
            {twitterImage ? <meta name="twitter:image" content={twitterImage} /> : null}

            {structuredDataItems.map((item, index) => (
                <script key={`json-ld-${index}`} type="application/ld+json">
                    {safeJsonLd(item)}
                </script>
            ))}
        </Helmet>
    );
}
