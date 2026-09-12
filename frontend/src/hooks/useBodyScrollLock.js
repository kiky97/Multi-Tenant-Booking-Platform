import { useLayoutEffect } from 'react';

const BODY_SCROLL_LOCK_CLASS = 'body-scroll-locked';

let activeLockCount = 0;
let lockedScrollY = 0;
let previousBodyStyles = null;

function lockBodyScroll() {
    if (typeof window === 'undefined' || typeof document === 'undefined') {
        return;
    }

    if (activeLockCount === 0) {
        const { body, documentElement } = document;
        const scrollbarWidth = Math.max(0, window.innerWidth - documentElement.clientWidth);

        lockedScrollY = window.scrollY;
        previousBodyStyles = {
            position: body.style.position,
            top: body.style.top,
            left: body.style.left,
            right: body.style.right,
            width: body.style.width,
            overflow: body.style.overflow,
            paddingRight: body.style.paddingRight
        };

        documentElement.classList.add(BODY_SCROLL_LOCK_CLASS);
        body.style.position = 'fixed';
        body.style.top = `-${lockedScrollY}px`;
        body.style.left = '0';
        body.style.right = '0';
        body.style.width = '100%';
        body.style.overflow = 'hidden';

        if (scrollbarWidth > 0) {
            body.style.paddingRight = `${scrollbarWidth}px`;
        }
    }

    activeLockCount += 1;
}

function unlockBodyScroll() {
    if (typeof window === 'undefined' || typeof document === 'undefined' || activeLockCount === 0) {
        return;
    }

    activeLockCount -= 1;

    if (activeLockCount > 0) {
        return;
    }

    const { body, documentElement } = document;
    const scrollY = lockedScrollY;

    documentElement.classList.remove(BODY_SCROLL_LOCK_CLASS);

    if (previousBodyStyles) {
        body.style.position = previousBodyStyles.position;
        body.style.top = previousBodyStyles.top;
        body.style.left = previousBodyStyles.left;
        body.style.right = previousBodyStyles.right;
        body.style.width = previousBodyStyles.width;
        body.style.overflow = previousBodyStyles.overflow;
        body.style.paddingRight = previousBodyStyles.paddingRight;
    } else {
        body.style.position = '';
        body.style.top = '';
        body.style.left = '';
        body.style.right = '';
        body.style.width = '';
        body.style.overflow = '';
        body.style.paddingRight = '';
    }

    previousBodyStyles = null;
    lockedScrollY = 0;
    window.scrollTo(0, scrollY);
}

export default function useBodyScrollLock(locked) {
    useLayoutEffect(() => {
        if (!locked) {
            return undefined;
        }

        lockBodyScroll();

        return () => {
            unlockBodyScroll();
        };
    }, [locked]);
}
