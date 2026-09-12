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

export function primeStripeOrigin() {
    ensureHeadLink({ rel: 'dns-prefetch', href: 'https://js.stripe.com' });
    ensureHeadLink({ rel: 'preconnect', href: 'https://js.stripe.com', crossOrigin: true });
}
