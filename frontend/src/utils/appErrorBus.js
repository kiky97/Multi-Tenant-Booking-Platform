import { logAppIssue } from './logger';

const APP_ERROR_EVENT = 'salon-booking-platform:error';

function cleanMessage(value) {
    return typeof value === 'string' ? value.trim() : '';
}

function collapseWhitespace(value) {
    return cleanMessage(value).replace(/\s+/g, ' ');
}

function normalizeErrorMessageCandidate(value) {
    const message = cleanMessage(value);

    if (!message) {
        return '';
    }

    const looksLikeHtml = /<!doctype html/i.test(message) || /<\/?[a-z][\s\S]*>/i.test(message);

    if (!looksLikeHtml) {
        return collapseWhitespace(message);
    }

    const stripped = collapseWhitespace(
        message
            .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, ' ')
            .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ' ')
            .replace(/<[^>]*>/g, ' ')
    );

    if (!stripped) {
        return '';
    }

    if (
        /^error response\b/i.test(stripped) ||
        /nothing matches the given uri/i.test(stripped) ||
        /file not found/i.test(stripped)
    ) {
        return '';
    }

    return stripped;
}

function collectFieldErrorMessages(fieldErrors) {
    if (!fieldErrors || typeof fieldErrors !== 'object') {
        return [];
    }

    return [...new Set(
        Object.values(fieldErrors)
            .map((value) => normalizeErrorMessageCandidate(String(value || '')))
            .filter(Boolean)
    )];
}

export function extractFriendlyErrorMessage(
    error,
    fallback = 'Something went wrong. Please try again.'
) {
    const directMessage = normalizeErrorMessageCandidate(error);
    if (directMessage) {
        return directMessage;
    }

    const fieldMessages = collectFieldErrorMessages(error?.fieldErrors || error?.response?.data?.fieldErrors);
    if (fieldMessages.length === 1) {
        return fieldMessages[0];
    }
    if (fieldMessages.length > 1) {
        return fieldMessages.join(' ');
    }

    const responseData = error?.response?.data;
    const responseMessage =
        normalizeErrorMessageCandidate(responseData?.message) ||
        normalizeErrorMessageCandidate(responseData?.error) ||
        normalizeErrorMessageCandidate(responseData);
    if (responseMessage) {
        return responseMessage;
    }

    const status = error?.status ?? error?.response?.status;
    if (status === 401) {
        return 'Your session has expired. Please sign in again.';
    }
    if (status === 403) {
        return 'You do not have permission to perform this action.';
    }
    if (status === 404) {
        return 'We could not find the information you requested.';
    }
    if (status === 409) {
        return 'That information changed just now. Please refresh and try again.';
    }
    if (status === 422) {
        return 'Some details need your attention before we can continue.';
    }
    if (status === 429) {
        return 'Too many requests were sent. Please wait a moment and try again.';
    }
    if (typeof status === 'number' && status >= 500) {
        return 'The server is having trouble right now. Please try again in a moment.';
    }

    if (error?.code === 'ERR_NETWORK' || error?.name === 'TypeError') {
        return 'We could not reach the server. Please check your connection and try again.';
    }

    const genericMessage = normalizeErrorMessageCandidate(error?.message);
    if (genericMessage) {
        return genericMessage;
    }

    return fallback;
}

export function reportAppError(error, options = {}) {
    const {
        message = '',
        source = 'app',
        notify = true,
        level = 'error'
    } = options;

    const friendlyMessage = cleanMessage(message) || extractFriendlyErrorMessage(error);
    const payload = {
        level,
        message: friendlyMessage,
        source,
        timestamp: Date.now()
    };

    logAppIssue(level, `[${source}] ${friendlyMessage}`, error);

    if (notify && typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
        window.dispatchEvent(new CustomEvent(APP_ERROR_EVENT, { detail: payload }));
    }

    return friendlyMessage;
}
