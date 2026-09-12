const requestedSectionIds = new Set();
const sectionRequestListeners = new Set();

export function requestDeferredSectionMount(sectionId) {
    if (!sectionId) {
        return;
    }

    requestedSectionIds.add(sectionId);

    sectionRequestListeners.forEach((listener) => {
        listener(sectionId);
    });
}

export function isDeferredSectionRequested(sectionId) {
    return Boolean(sectionId) && requestedSectionIds.has(sectionId);
}

export function subscribeToDeferredSectionRequests(listener) {
    sectionRequestListeners.add(listener);

    return () => {
        sectionRequestListeners.delete(listener);
    };
}
