function ensureHeadLink({ rel, href, crossOrigin = false }) {
    if (typeof document === 'undefined' || !href) {
        return;
    }

    const head = document.head;
    if (!head || head.querySelector(`link[rel="${rel}"][href="${href}"]`)) {
        return;
    }

    const link = document.createElement('link');
    link.rel = rel;
    link.href = href;

    if (crossOrigin) {
        link.crossOrigin = '';
    }

    head.appendChild(link);
}

function resolveOrigin(url) {
    if (typeof window === 'undefined' || !url) {
        return '';
    }

    try {
        return new URL(url, window.location.origin).origin;
    } catch {
        return '';
    }
}

function preconnectToOrigin(url, { crossOrigin = false, allowSameOrigin = false } = {}) {
    const origin = resolveOrigin(url);

    if (!origin || (typeof window !== 'undefined' && !allowSameOrigin && origin === window.location.origin)) {
        return;
    }

    ensureHeadLink({ rel: 'dns-prefetch', href: origin });
    ensureHeadLink({ rel: 'preconnect', href: origin, crossOrigin });
}

export function primePublicApiOrigin() {
    const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').trim();

    if (!apiBaseUrl) {
        return;
    }

    preconnectToOrigin(apiBaseUrl);
}
