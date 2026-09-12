import { useLocation, useNavigate } from 'react-router-dom';
import { runElementScrollAlignmentSequence, scrollWindowToTop } from '../utils/scroll';
import { preparePendingNavigation } from '../utils/navigation';
import { requestDeferredSectionMount } from '../utils/deferredSections';

export default function SectionLink({
    sectionId,
    fallbackPath = '/',
    className = '',
    children,
    onNavigate
}) {
    const location = useLocation();
    const navigate = useNavigate();

    function handleClick(event) {
        event.preventDefault();

        const isSamePage = location.pathname === fallbackPath;

        if (isSamePage) {
            if (!sectionId) {
                scrollWindowToTop({
                    behavior: 'smooth',
                    hideScrollbar: false
                });
                onNavigate?.();
                return;
            }

            requestDeferredSectionMount(sectionId);
            const target = document.getElementById(sectionId);

            if (target) {
                runElementScrollAlignmentSequence(target, {
                    initialBehavior: 'smooth',
                    retryBehavior: 'instant',
                    extraOffset: 16,
                    hideScrollbar: false
                });
            } else {
                scrollWindowToTop({
                    behavior: 'smooth',
                    hideScrollbar: false
                });
            }

            onNavigate?.();
            return;
        }

        preparePendingNavigation(fallbackPath, sectionId || null);
        onNavigate?.();
        navigate(fallbackPath);
    }

    return (
        <a href={fallbackPath} onClick={handleClick} className={className}>
            {children}
        </a>
    );
}
