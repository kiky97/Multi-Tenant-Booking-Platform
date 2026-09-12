import { setAutoScrollHidden } from './scroll';
import { requestDeferredSectionMount } from './deferredSections';

export const PENDING_SCROLL_KEY = 'pending-section-scroll';
export const SECTION_SCROLL_PENDING_CLASS = 'section-scroll-pending';

export function setSectionScrollPending(isPending) {
    if (typeof document === 'undefined') {
        return;
    }

    document.documentElement.classList.toggle(SECTION_SCROLL_PENDING_CLASS, isPending);
    setAutoScrollHidden(isPending);
}

export function preparePendingNavigation(path, sectionId = null) {
    if (typeof window === 'undefined') {
        return;
    }

    sessionStorage.setItem(
        PENDING_SCROLL_KEY,
        JSON.stringify({
            path,
            sectionId,
            ts: Date.now()
        })
    );

    requestDeferredSectionMount(sectionId);
    setSectionScrollPending(true);
}

export function clearPendingNavigation() {
    if (typeof window !== 'undefined') {
        sessionStorage.removeItem(PENDING_SCROLL_KEY);
    }

    setSectionScrollPending(false);
}
