import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { FaInstagram, FaTiktok } from 'react-icons/fa';
import { Menu, Moon, Sun, X } from 'lucide-react';
import SectionLink from './SectionLink';
import {
    cancelActiveScrollSequence,
    runElementScrollAlignmentSequence,
    scrollWindowToTop,
    setAutoScrollHidden,
    setSiteHeaderHeight
} from '../utils/scroll';
import {
    PENDING_SCROLL_KEY,
    SECTION_SCROLL_PENDING_CLASS,
    preparePendingNavigation
} from '../utils/navigation';
import useBodyScrollLock from '../hooks/useBodyScrollLock';
import { useColorScheme } from '../context/ColorSchemeContext';
import { getThemeLogoPath } from '../utils/themeAssets';

function getObservedHeaderHeight(entry) {
    const borderBoxSize = Array.isArray(entry.borderBoxSize)
        ? entry.borderBoxSize[0]
        : entry.borderBoxSize;
    const height = borderBoxSize?.blockSize ?? entry.contentRect?.height ?? 0;

    return Number.isFinite(height) && height > 0 ? height : 0;
}

export default function Navbar() {
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
    const headerRef = useRef(null);
    const pendingMobileActionRef = useRef(null);
    const { colorScheme, isDarkMode, toggleColorScheme } = useColorScheme();
    const logoPath = getThemeLogoPath(colorScheme);
    const location = useLocation();
    const navigate = useNavigate();
    useBodyScrollLock(mobileMenuOpen);

    const navItemsLeft = [
        { label: 'Home', type: 'section', fallbackPath: '/', sectionId: null },
        { label: 'Gallery', type: 'section', fallbackPath: '/', sectionId: 'gallery' },
        { label: 'Services', type: 'route', to: '/services' }
    ];

    const navItemsRight = [
        { label: 'Book Now', type: 'route', to: '/booking' },
        { label: 'FAQ', type: 'route', to: '/faq' },
        { label: 'Contact', type: 'section', fallbackPath: location.pathname, sectionId: 'contact' }
    ];

    const mobileItems = [...navItemsLeft, ...navItemsRight];

    useEffect(() => {
        if (typeof ResizeObserver === 'undefined') {
            return undefined;
        }

        let frameId = null;
        let pendingHeight = 0;
        let lastAppliedHeight = 0;

        const applyPendingHeight = () => {
            frameId = null;

            if (!pendingHeight || Math.abs(pendingHeight - lastAppliedHeight) < 0.5) {
                return;
            }

            lastAppliedHeight = pendingHeight;
            setSiteHeaderHeight(pendingHeight);
        };

        const scheduleHeaderHeightUpdate = (height) => {
            if (!height) {
                return;
            }

            pendingHeight = height;

            if (frameId != null) {
                return;
            }

            frameId = window.requestAnimationFrame(applyPendingHeight);
        };

        const observer = new ResizeObserver((entries) => {
            const nextHeight = getObservedHeaderHeight(entries[0]);
            scheduleHeaderHeightUpdate(nextHeight);
        });

        const headerNode = headerRef.current;

        if (headerNode) {
            observer.observe(headerNode);
        }

        return () => {
            if (frameId != null) {
                window.cancelAnimationFrame(frameId);
            }
            observer.disconnect();
        };
    }, []);

    function closeMenu() {
        setMobileMenuOpen(false);
    }

    function runAfterMobileMenuCloses(action) {
        if (typeof action !== 'function') {
            setMobileMenuOpen(false);
            return;
        }

        if (!mobileMenuOpen) {
            action();
            return;
        }

        pendingMobileActionRef.current = action;
        setMobileMenuOpen(false);
    }

    function persistPendingSectionScroll(path, sectionId) {
        sessionStorage.setItem(
            PENDING_SCROLL_KEY,
            JSON.stringify({
                path,
                sectionId: sectionId || null,
                ts: Date.now()
            })
        );
    }

    function clearPendingSectionScroll() {
        sessionStorage.removeItem(PENDING_SCROLL_KEY);

        if (typeof document !== 'undefined') {
            document.documentElement.classList.remove(SECTION_SCROLL_PENDING_CLASS);
            setAutoScrollHidden(false);
        }
    }

    function navigateToRouteTop(path) {
        cancelActiveScrollSequence();

        if (location.pathname === path) {
            scrollWindowToTop({
                behavior: 'smooth',
                hideScrollbar: false
            });
            return;
        }

        preparePendingNavigation(path, null);
        navigate(path);
    }

    function repeatMobileScrollToTop() {
        cancelActiveScrollSequence();

        scrollWindowToTop({
            behavior: 'instant',
            hideScrollbar: false
        });

        window.requestAnimationFrame(() => {
            scrollWindowToTop({
                behavior: 'instant',
                hideScrollbar: false
            });
        });

        window.setTimeout(() => {
            scrollWindowToTop({
                behavior: 'instant',
                hideScrollbar: false
            });
        }, 90);
    }

    function repeatMobileScrollToElement(element) {
        if (!element) {
            repeatMobileScrollToTop();
            return;
        }

        runElementScrollAlignmentSequence(element, {
            initialBehavior: 'instant',
            retryBehavior: 'instant',
            extraOffset: 16,
            hideScrollbar: false,
            retryDelays: [120, 300, 700],
            maxDuration: 1000
        });
    }

    function handleMobileRouteNavigation(path) {
        runAfterMobileMenuCloses(() => {
            cancelActiveScrollSequence();
            clearPendingSectionScroll();

            if (location.pathname === path) {
                scrollWindowToTop({
                    behavior: 'smooth',
                    hideScrollbar: false
                });
                return;
            }

            preparePendingNavigation(path, null);
            navigate(path);
        });
    }

    function handleMobileSectionNavigation(sectionId, fallbackPath = '/') {
        runAfterMobileMenuCloses(() => {
            cancelActiveScrollSequence();
            const isSamePage = location.pathname === fallbackPath;

            if (isSamePage) {
                if (!sectionId) {
                    repeatMobileScrollToTop();
                    return;
                }

                const target = document.getElementById(sectionId);

                if (target) {
                    repeatMobileScrollToElement(target);
                } else {
                    repeatMobileScrollToTop();
                }

                return;
            }

            persistPendingSectionScroll(fallbackPath, sectionId);
            navigate(fallbackPath);
        });
    }

    function renderMobileNavItem(item) {
        const href = item.type === 'route' ? item.to : (item.fallbackPath || '/');

        return (
            <a
                key={item.label}
                href={href}
                className="mobile-nav-link"
                onClick={(event) => {
                    event.preventDefault();

                    if (item.type === 'route') {
                        handleMobileRouteNavigation(item.to);
                        return;
                    }

                    handleMobileSectionNavigation(item.sectionId, item.fallbackPath || '/');
                }}
            >
                {item.label}
            </a>
        );
    }

    useEffect(() => {
        if (mobileMenuOpen || !pendingMobileActionRef.current) {
            return undefined;
        }

        const action = pendingMobileActionRef.current;
        pendingMobileActionRef.current = null;

        const timeoutId = window.setTimeout(() => {
            action();
        }, 40);

        return () => {
            window.clearTimeout(timeoutId);
        };
    }, [mobileMenuOpen]);

    function renderNavItem(item, className = 'nav-link', onClick) {
        if (item.type === 'route') {
            return (
                <a
                    key={item.label}
                    href={item.to}
                    className={className}
                    onClick={(event) => {
                        event.preventDefault();
                        onClick?.();
                        navigateToRouteTop(item.to);
                    }}
                >
                    {item.label}
                </a>
            );
        }

        return (
            <SectionLink
                key={item.label}
                sectionId={item.sectionId}
                fallbackPath={item.fallbackPath || '/'}
                className={className}
                onNavigate={onClick}
            >
                {item.label}
            </SectionLink>
        );
    }

    function renderColorSchemeToggle(className = 'nav-theme-toggle') {
        const nextModeLabel = isDarkMode ? 'Switch to light mode' : 'Switch to dark mode';

        return (
            <button
                type="button"
                className={className}
                onClick={toggleColorScheme}
                aria-label={nextModeLabel}
                aria-pressed={colorScheme === 'light'}
                title={nextModeLabel}
            >
                {isDarkMode ? <Sun size={18} strokeWidth={1.8} /> : <Moon size={18} strokeWidth={1.8} />}
            </button>
        );
    }

    return (
        <>
            <header
                ref={headerRef}
                className="site-header"
            >
                <div className="nav-shell py-3 text-[11px] uppercase text-ivory">
                    <div className="hidden md:grid nav-desktop-grid items-center">
                        <div className="flex items-center gap-6">
                            {navItemsLeft.map((item) => renderNavItem(item))}
                        </div>

                        <Link
                            to="/"
                            className="flex justify-center"
                            onClick={(event) => {
                                event.preventDefault();
                                navigateToRouteTop('/');
                            }}
                        >
                            <div className="nav-logo-wrap">
                                <img
                                    src={logoPath}
                                    alt="Royal Chair logo"
                                    width="320"
                                    height="320"
                                    loading="eager"
                                    decoding="async"
                                    fetchPriority="high"
                                    className="w-full h-full object-cover"
                                />
                            </div>
                        </Link>

                        <div className="flex items-center justify-end gap-6">
                            {navItemsRight.map((item) => renderNavItem(item))}

                            <div className="nav-socials">
                                <a
                                    href="https://www.instagram.com/royalchair.ie"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="nav-social-link"
                                    aria-label="Instagram"
                                    title="Instagram"
                                >
                                    <FaInstagram size={16} />
                                </a>

                                <a
                                    href="https://www.tiktok.com/@royalchair.ie"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="nav-social-link"
                                    aria-label="TikTok"
                                    title="TikTok"
                                >
                                    <FaTiktok size={16} />
                                </a>

                                {renderColorSchemeToggle()}
                            </div>
                        </div>
                    </div>

                    <div className="md:hidden flex items-center justify-between gap-4">
                        <a
                            href="/"
                            className="flex items-center gap-3"
                            onClick={(event) => {
                                event.preventDefault();
                                handleMobileSectionNavigation(null, '/');
                            }}
                        >
                            <div className="nav-logo-wrap nav-logo-mobile">
                                <img
                                    src={logoPath}
                                    alt="Royal Chair logo"
                                    width="320"
                                    height="320"
                                    loading="eager"
                                    decoding="async"
                                    fetchPriority="high"
                                    className="w-full h-full object-cover"
                                />
                            </div>
                            <div className="nav-mobile-brand">
                                <span className="nav-mobile-brand-title">Royal Chair</span>
                                <span className="nav-mobile-brand-subtitle">Barbershop</span>
                            </div>
                        </a>

                        <div className="nav-mobile-actions">
                            {renderColorSchemeToggle('nav-theme-toggle nav-theme-toggle-mobile')}

                            <button
                                type="button"
                                className={`nav-burger-btn ${mobileMenuOpen ? 'nav-burger-btn-open' : ''}`}
                                aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
                                aria-expanded={mobileMenuOpen}
                                onClick={() => setMobileMenuOpen((prev) => !prev)}
                            >
                                {mobileMenuOpen ? <X size={24} /> : <Menu size={24} />}
                            </button>
                        </div>
                    </div>
                </div>
            </header>
            <div className="site-header-spacer" aria-hidden="true" />

            <div className={`mobile-nav-overlay ${mobileMenuOpen ? 'mobile-nav-overlay-open' : ''}`}>
                <div className={`mobile-nav-panel ${mobileMenuOpen ? 'mobile-nav-panel-open' : ''}`}>
                    <div className="mobile-nav-top">
                        <a
                            href="/"
                            className="flex items-center gap-3"
                            onClick={(event) => {
                                event.preventDefault();
                                handleMobileSectionNavigation(null, '/');
                            }}
                        >
                            <div className="nav-logo-wrap nav-logo-mobile-menu">
                                <img
                                    src={logoPath}
                                    alt="Royal Chair logo"
                                    width="320"
                                    height="320"
                                    loading="lazy"
                                    decoding="async"
                                    fetchPriority="low"
                                    className="w-full h-full object-cover"
                                />
                            </div>
                            <div className="nav-mobile-brand">
                                <span className="nav-mobile-brand-title">Royal Chair</span>
                                <span className="nav-mobile-brand-subtitle">Barbershop</span>
                            </div>
                        </a>

                        <div className="nav-mobile-actions">
                            {renderColorSchemeToggle('nav-theme-toggle nav-theme-toggle-mobile')}

                            <button
                                type="button"
                                className="nav-burger-btn nav-burger-btn-open"
                                aria-label="Close menu"
                                onClick={closeMenu}
                            >
                                <X size={24} />
                            </button>
                        </div>
                    </div>

                    <div className="mobile-nav-links">
                        {mobileItems.map((item, index) => (
                            <div
                                key={item.label}
                                className="mobile-nav-link-wrap"
                                style={{ transitionDelay: `${120 + index * 55}ms` }}
                            >
                                {renderMobileNavItem(item)}
                            </div>
                        ))}
                    </div>

                    <div className="mobile-nav-bottom">
                        <div className="mobile-nav-socials">
                            <a
                                href="https://www.instagram.com/royalchair.ie"
                                target="_blank"
                                rel="noopener noreferrer"
                                className="mobile-nav-social-link"
                                aria-label="Instagram"
                                title="Instagram"
                            >
                                <FaInstagram size={20} />
                            </a>

                            <a
                                href="https://www.tiktok.com/@royalchair.ie"
                                target="_blank"
                                rel="noopener noreferrer"
                                className="mobile-nav-social-link"
                                aria-label="TikTok"
                                title="TikTok"
                            >
                                <FaTiktok size={20} />
                            </a>
                        </div>

                        <div className="mobile-nav-caption">Royal style. Sharp detail. Premium finish.</div>
                    </div>
                </div>
            </div>
        </>
    );
}
