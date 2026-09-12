const DEFAULT_SITE_HEADER_HEIGHT = 108;
const AUTO_SCROLL_HIDDEN_CLASS = 'auto-scroll-hidden';

let autoScrollHideTimeoutId = null;
let activeScrollSequenceCleanup = null;
let siteHeaderHeight = DEFAULT_SITE_HEADER_HEIGHT;
let lastWrittenSiteHeaderHeight = DEFAULT_SITE_HEADER_HEIGHT;
let elementScrollSnapshotCache = new WeakMap();
let elementScrollSnapshotResetId = null;

export function cancelActiveScrollSequence() {
    if (!activeScrollSequenceCleanup) {
        return;
    }

    const cleanup = activeScrollSequenceCleanup;
    activeScrollSequenceCleanup = null;
    cleanup();
}

export function setActiveScrollSequence(cleanup) {
    cancelActiveScrollSequence();

    if (typeof cleanup !== 'function') {
        return () => { };
    }

    let cleanedUp = false;

    const wrappedCleanup = () => {
        if (cleanedUp) {
            return;
        }

        cleanedUp = true;
        cleanup();
    };

    activeScrollSequenceCleanup = wrappedCleanup;

    return () => {
        if (activeScrollSequenceCleanup === wrappedCleanup) {
            activeScrollSequenceCleanup = null;
        }

        wrappedCleanup();
    };
}

function getSiteHeaderHeight() {
    return siteHeaderHeight;
}

function getSiteHeaderOffset(extraOffset = 16) {
    return getSiteHeaderHeight() + extraOffset;
}

function resetElementScrollSnapshotCache() {
    elementScrollSnapshotCache = new WeakMap();
    elementScrollSnapshotResetId = null;
}

function scheduleElementScrollSnapshotReset() {
    if (typeof window === 'undefined' || elementScrollSnapshotResetId != null) {
        return;
    }

    elementScrollSnapshotResetId = window.requestAnimationFrame(resetElementScrollSnapshotCache);
}

function readElementViewportSnapshotOncePerFrame(element) {
    if (!element) {
        return null;
    }

    const cachedSnapshot = elementScrollSnapshotCache.get(element);

    if (cachedSnapshot) {
        return cachedSnapshot;
    }

    const rect = element.getBoundingClientRect();

    const snapshot = {
        top: rect.top,
        scrollY: window.scrollY
    };

    elementScrollSnapshotCache.set(element, snapshot);
    scheduleElementScrollSnapshotReset();

    return snapshot;
}

export function readElementScrollTargetOncePerFrame(element, extraOffset = 16) {
    if (typeof window === 'undefined' || !element) {
        return null;
    }

    const snapshot = readElementViewportSnapshotOncePerFrame(element);

    if (!snapshot) {
        return null;
    }

    return Math.max(
        0,
        snapshot.top + snapshot.scrollY - getSiteHeaderOffset(extraOffset)
    );
}

function getScrollTopForElement(element, extraOffset = 16) {
    if (typeof window === 'undefined' || !element) {
        return null;
    }

    return readElementScrollTargetOncePerFrame(element, extraOffset);
}

export function setSiteHeaderHeight(height) {
    if (typeof document === 'undefined') {
        return;
    }

    const normalizedHeight =
        Number.isFinite(height) && height > 0 ? height : DEFAULT_SITE_HEADER_HEIGHT;

    siteHeaderHeight = normalizedHeight;

    if (Math.abs(normalizedHeight - lastWrittenSiteHeaderHeight) < 0.5) {
        return;
    }

    lastWrittenSiteHeaderHeight = normalizedHeight;
    document.documentElement.style.setProperty('--site-header-height', `${normalizedHeight}px`);
}

export function setAutoScrollHidden(isHidden) {
    if (typeof document === 'undefined') {
        return;
    }

    document.documentElement.classList.toggle(AUTO_SCROLL_HIDDEN_CLASS, isHidden);
}

function hideAutoScrollbarsTemporarily(duration = 520) {
    if (typeof window === 'undefined') {
        return;
    }

    setAutoScrollHidden(true);

    if (autoScrollHideTimeoutId) {
        window.clearTimeout(autoScrollHideTimeoutId);
    }

    autoScrollHideTimeoutId = window.setTimeout(() => {
        autoScrollHideTimeoutId = null;
        setAutoScrollHidden(false);
    }, duration);
}

function resolveAutoScrollHideDuration(behavior, explicitDuration) {
    if (explicitDuration != null) {
        return explicitDuration;
    }

    return behavior === 'smooth' ? 520 : 180;
}

export function scrollWindowToTop({
    behavior = 'smooth',
    hideScrollbar = false,
    hideScrollbarDuration
} = {}) {
    if (typeof window === 'undefined') {
        return;
    }

    if (hideScrollbar) {
        hideAutoScrollbarsTemporarily(resolveAutoScrollHideDuration(behavior, hideScrollbarDuration));
    }

    window.scrollTo({
        top: 0,
        left: 0,
        behavior
    });
}

export function scrollWindowToElement(
    element,
    { behavior = 'smooth', extraOffset = 16, hideScrollbar = false, hideScrollbarDuration } = {}
) {
    const top = getScrollTopForElement(element, extraOffset);

    if (top == null) {
        return;
    }

    if (hideScrollbar) {
        hideAutoScrollbarsTemporarily(resolveAutoScrollHideDuration(behavior, hideScrollbarDuration));
    }

    window.scrollTo({
        top,
        left: 0,
        behavior
    });
}

export function runElementScrollAlignmentSequence(
    element,
    {
        initialBehavior = 'smooth',
        retryBehavior = 'instant',
        extraOffset = 16,
        hideScrollbar = false,
        hideScrollbarDuration,
        retryDelays = [120, 260, 520, 900],
        maxDuration = 1400
    } = {}
) {
    cancelActiveScrollSequence();

    if (typeof window === 'undefined' || !element) {
        return () => { };
    }

    const timeoutIds = [];
    const frameIds = [];
    let cancelled = false;
    let disposeSequence = null;

    const alignToElement = (behavior) => {
        if (cancelled || !element.isConnected) {
            return;
        }

        scrollWindowToElement(element, {
            behavior,
            extraOffset,
            hideScrollbar,
            hideScrollbarDuration
        });
    };

    alignToElement(initialBehavior);

    frameIds.push(
        window.requestAnimationFrame(() => {
            alignToElement(retryBehavior);
        })
    );

    frameIds.push(
        window.requestAnimationFrame(() => {
            frameIds.push(
                window.requestAnimationFrame(() => {
                    alignToElement(retryBehavior);
                })
            );
        })
    );

    retryDelays.forEach((delay) => {
        timeoutIds.push(
            window.setTimeout(() => {
                alignToElement(retryBehavior);
            }, delay)
        );
    });

    const stopForUserIntent = () => {
        disposeSequence?.();
    };

    window.addEventListener('wheel', stopForUserIntent, { passive: true, once: true });
    window.addEventListener('touchstart', stopForUserIntent, { passive: true, once: true });
    window.addEventListener('keydown', stopForUserIntent, { once: true });

    const cleanup = () => {
        cancelled = true;
        frameIds.forEach((id) => window.cancelAnimationFrame(id));
        timeoutIds.forEach((id) => window.clearTimeout(id));
        window.removeEventListener('wheel', stopForUserIntent);
        window.removeEventListener('touchstart', stopForUserIntent);
        window.removeEventListener('keydown', stopForUserIntent);
    };

    disposeSequence = setActiveScrollSequence(cleanup);
    timeoutIds.push(
        window.setTimeout(() => {
            disposeSequence?.();
        }, maxDuration)
    );

    return disposeSequence;
}
