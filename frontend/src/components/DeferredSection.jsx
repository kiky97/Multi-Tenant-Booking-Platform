import { Suspense, useEffect, useRef, useState } from 'react';
import {
    isDeferredSectionRequested,
    subscribeToDeferredSectionRequests
} from '../utils/deferredSections';

function DeferredSectionPlaceholder({
    className = 'services-page-shell py-8',
    minHeight = 640
}) {
    return (
        <div className={className} aria-hidden="true">
            <div style={{ minHeight }} />
        </div>
    );
}

export default function DeferredSection({
    sectionId,
    children,
    placeholderClassName = 'services-page-shell py-8',
    placeholderMinHeight = 640,
    rootMargin = '200px 0px 200px 0px'
}) {
    const anchorRef = useRef(null);
    const [shouldRender, setShouldRender] = useState(() => isDeferredSectionRequested(sectionId));

    useEffect(() => {
        if (!sectionId) {
            return undefined;
        }

        return subscribeToDeferredSectionRequests((requestedSectionId) => {
            if (requestedSectionId === sectionId) {
                setShouldRender(true);
            }
        });
    }, [sectionId]);

    useEffect(() => {
        if (shouldRender) {
            return undefined;
        }

        const anchorNode = anchorRef.current;

        if (!anchorNode || typeof IntersectionObserver === 'undefined') {
            setShouldRender(true);
            return undefined;
        }

        const observer = new IntersectionObserver(
            ([entry]) => {
                if (entry?.isIntersecting || entry?.intersectionRatio > 0) {
                    setShouldRender(true);
                    observer.disconnect();
                }
            },
            { rootMargin }
        );

        observer.observe(anchorNode);

        return () => {
            observer.disconnect();
        };
    }, [rootMargin, shouldRender]);

    return (
        <div
            id={sectionId || undefined}
            ref={anchorRef}
            data-deferred-section-anchor={sectionId || undefined}
        >
            {shouldRender ? (
                <Suspense
                    fallback={
                        <DeferredSectionPlaceholder
                            className={placeholderClassName}
                            minHeight={placeholderMinHeight}
                        />
                    }
                >
                    {children}
                </Suspense>
            ) : (
                <DeferredSectionPlaceholder
                    className={placeholderClassName}
                    minHeight={placeholderMinHeight}
                />
            )}
        </div>
    );
}
