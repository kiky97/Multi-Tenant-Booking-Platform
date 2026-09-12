import React from 'react';
import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

vi.mock('framer-motion', () => {
    const motionProps = new Set([
        'animate',
        'exit',
        'initial',
        'layout',
        'transition',
        'variants',
        'viewport',
        'whileHover',
        'whileInView',
        'whileTap'
    ]);

    const createMotionComponent = (tag) => {
        const MotionComponent = React.forwardRef(({ children, ...props }, ref) => {
            const domProps = Object.fromEntries(
                Object.entries(props).filter(([key]) => !motionProps.has(key))
            );

            return React.createElement(tag, { ...domProps, ref }, children);
        });
        MotionComponent.displayName = `MockMotion(${String(tag)})`;
        return MotionComponent;
    };

    return {
        useReducedMotion: () => false,
        motion: new Proxy({}, {
            get: (_, tag) => createMotionComponent(tag)
        })
    };
});

class TestResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
}

class TestIntersectionObserver {
    constructor(callback) {
        this.callback = callback;
    }

    observe(target) {
        this.callback([{ isIntersecting: true, intersectionRatio: 1, target }], this);
    }

    unobserve() {}
    disconnect() {}
}

let frameId = 0;
const frameTimers = new Map();

Object.defineProperty(window, 'ResizeObserver', {
    writable: true,
    value: TestResizeObserver
});

Object.defineProperty(window, 'IntersectionObserver', {
    writable: true,
    value: TestIntersectionObserver
});

Object.defineProperty(window, 'requestAnimationFrame', {
    writable: true,
    value: vi.fn((callback) => {
        frameId += 1;
        const id = frameId;
        const timerId = window.setTimeout(() => {
            frameTimers.delete(id);
            callback(performance.now());
        }, 0);
        frameTimers.set(id, timerId);
        return id;
    })
});

Object.defineProperty(window, 'cancelAnimationFrame', {
    writable: true,
    value: vi.fn((id) => {
        const timerId = frameTimers.get(id);
        if (timerId) {
            window.clearTimeout(timerId);
            frameTimers.delete(id);
        }
    })
});

Object.defineProperty(window, 'scrollTo', {
    writable: true,
    value: vi.fn()
});

Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn((query) => ({
        matches: false,
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn()
    }))
});

Object.defineProperty(HTMLMediaElement.prototype, 'play', {
    writable: true,
    value: vi.fn(() => Promise.resolve())
});

afterEach(() => {
    cleanup();
    frameTimers.forEach((timerId) => window.clearTimeout(timerId));
    frameTimers.clear();
    document.documentElement.className = '';
    document.documentElement.removeAttribute('data-theme');
    document.body.className = '';
    document.body.removeAttribute('data-theme');
    document.body.removeAttribute('data-admin-app');
    document.body.removeAttribute('style');
    window.sessionStorage.clear();
    window.localStorage.clear();
    vi.clearAllMocks();
});
