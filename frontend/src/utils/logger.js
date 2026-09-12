const SAFE_LOG_LEVELS = new Set(['error', 'warn']);
const SENSITIVE_KEY_PATTERN = /(authorization|cookie|password|secret|token|clientsecret|payment|card)/i;

function cleanText(value) {
    return typeof value === 'string' ? value.replace(/\s+/g, ' ').trim() : '';
}

function sanitizeUrl(value) {
    const text = cleanText(value);

    if (!text) {
        return '';
    }

    try {
        const url = new URL(text, window.location.origin);
        url.search = '';
        url.hash = '';
        return url.toString();
    } catch {
        return text.split('?')[0].split('#')[0];
    }
}

function sanitizePlainObject(value, depth = 0) {
    if (!value || typeof value !== 'object' || depth > 1) {
        return undefined;
    }

    return Object.entries(value).reduce((safe, [key, entry]) => {
        if (SENSITIVE_KEY_PATTERN.test(key)) {
            safe[key] = '[redacted]';
            return safe;
        }

        if (entry == null || ['string', 'number', 'boolean'].includes(typeof entry)) {
            safe[key] = typeof entry === 'string' ? cleanText(entry) : entry;
            return safe;
        }

        if (typeof entry === 'object' && !Array.isArray(entry)) {
            safe[key] = sanitizePlainObject(entry, depth + 1);
        }

        return safe;
    }, {});
}

function sanitizeError(error) {
    if (!error) {
        return undefined;
    }

    if (typeof error === 'string') {
        return { message: cleanText(error) };
    }

    if (typeof error !== 'object') {
        return { message: cleanText(String(error)) };
    }

    const status = error.status ?? error.response?.status;
    const method = error.config?.method ? String(error.config.method).toUpperCase() : undefined;
    const url = error.config?.url || error.request?.responseURL || error.url;
    const safe = {
        name: cleanText(error.name),
        message: cleanText(error.message),
        code: cleanText(error.code),
        status,
        method,
        url: sanitizeUrl(url)
    };

    const details = sanitizePlainObject(error.details || error.payload);
    if (details && Object.keys(details).length > 0) {
        safe.details = details;
    }

    return Object.fromEntries(
        Object.entries(safe).filter(([, value]) => value !== undefined && value !== '')
    );
}

export function logAppIssue(level, message, error) {
    const normalizedLevel = level === 'warn' ? 'warn' : 'error';

    if (!SAFE_LOG_LEVELS.has(normalizedLevel) || typeof console === 'undefined') {
        return;
    }

    const logger = typeof console[normalizedLevel] === 'function'
        ? console[normalizedLevel]
        : console.error;
    const cleanMessage = cleanText(message) || 'Application issue';
    const safeError = sanitizeError(error);

    if (safeError && Object.keys(safeError).length > 0) {
        logger(cleanMessage, safeError);
        return;
    }

    logger(cleanMessage);
}
