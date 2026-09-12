import { useMemo } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Phone, MapPin, Clock, ExternalLink, Navigation } from 'lucide-react';
import { FaInstagram, FaTiktok } from 'react-icons/fa';
import useHairSalonInfo from '../hooks/useHairSalonInfo';
import { useColorScheme } from '../context/ColorSchemeContext';
import SectionLink from './SectionLink';
import { getThemeLogoPath } from '../utils/themeAssets';

const dayOrder = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const GOOGLE_MAPS_PLACE_URL =
    'https://www.google.com/maps/search/?api=1&query=Royal%20Chair%2C%20Ennis%2C%20Ireland';
const GOOGLE_MAPS_DIRECTIONS_URL =
    'https://www.google.com/maps/dir/?api=1&destination=Royal%20Chair%2C%20Ennis%2C%20Ireland&travelmode=driving';

function formatHours(workingHours) {
    if (!Array.isArray(workingHours) || workingHours.length === 0) return null;

    const sorted = [...workingHours].sort(
        (a, b) => dayOrder.indexOf(a.dayOfWeek) - dayOrder.indexOf(b.dayOfWeek)
    );

    return sorted
        .map((day) => {
            const label = day.dayOfWeek.slice(0, 3).toLowerCase();
            const titleLabel = label.charAt(0).toUpperCase() + label.slice(1);

            if (!day.workingDay) return `${titleLabel}: Closed`;

            return `${titleLabel}: ${day.openTime?.slice(0, 5)}-${day.closeTime?.slice(0, 5)}`;
        })
        .join(' | ');
}

export default function Footer() {
    const info = useHairSalonInfo();
    const { colorScheme } = useColorScheme();
    const hoursText = useMemo(() => formatHours(info?.workingHours), [info]);
    const logoPath = getThemeLogoPath(colorScheme);
    const location = useLocation();
    const navigate = useNavigate();

    function navigateToRouteTop(path) {
        if (location.pathname === path) {
            window.scrollTo({ top: 0, left: 0, behavior: 'smooth' });
            return;
        }

        sessionStorage.setItem(
            'pending-section-scroll',
            JSON.stringify({
                path,
                sectionId: null,
                ts: Date.now()
            })
        );

        if (typeof document !== 'undefined') {
            document.documentElement.classList.add('section-scroll-pending');
        }

        navigate(path);
    }

    return (
        <footer className="site-footer mt-14 border-t border-[#c6934b30] bg-[#040404]/88 pb-10 pt-10" id="contact">
            <div className="services-page-shell">
                <div className="site-footer-panel glass-card gold-border relative overflow-hidden rounded-[28px] border border-[#c6934b40] bg-[radial-gradient(circle_at_top_left,rgba(227,192,122,0.08),transparent_24%),radial-gradient(circle_at_bottom_right,rgba(255,255,255,0.03),transparent_30%),linear-gradient(180deg,rgba(11,11,11,0.97),rgba(3,3,3,0.96))] p-6 sm:p-7 lg:p-8">
                    <div
                        className="site-footer-divider pointer-events-none absolute inset-y-8 left-1/2 hidden w-px -translate-x-1/2 bg-[linear-gradient(180deg,transparent,rgba(227,192,122,0.18),transparent)] lg:block"
                        aria-hidden="true"
                    />

                    <div className="grid gap-8 lg:grid-cols-[minmax(0,0.92fr)_minmax(0,1.08fr)] lg:gap-10">
                        <div className="flex h-full flex-col justify-between gap-8 lg:pr-8">
                            <div className="space-y-6">
                                <div className="flex items-center gap-4">
                                    <div className="site-footer-logo h-20 w-20 overflow-hidden rounded-full border border-[#c6934b60] bg-[#080808] shadow-gold">
                                        <img
                                            src={logoPath}
                                            alt="Royal Chair logo"
                                            width="320"
                                            height="320"
                                            loading="lazy"
                                            decoding="async"
                                            fetchPriority="low"
                                            className="h-full w-full object-cover"
                                        />
                                    </div>

                                    <div className="min-w-0">
                                        <p className="site-footer-kicker text-[11px] uppercase tracking-[0.26em] text-white">
                                            Visit Royal Chair
                                        </p>
                                        <h3 className="site-footer-title mt-2 font-heading text-[1.2rem] uppercase tracking-[0.12em] text-ivory sm:text-[1.35rem]">
                                            Find Us In Google Maps
                                        </h3>
                                    </div>
                                </div>

                                <div className="space-y-4">
                                    <p className="site-footer-description max-w-[42ch] text-sm leading-7 text-smoke sm:text-[0.96rem]">
                                        Open the full Royal Chair location in Google Maps, check the latest contact
                                        details, or launch turn-by-turn directions straight from your device.
                                    </p>
                                </div>
                            </div>

                            <div className="grid gap-3 pt-1 sm:grid-cols-2">
                                <a
                                    href={GOOGLE_MAPS_PLACE_URL}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="site-footer-primary-action inline-flex min-h-[56px] items-center justify-center gap-2 rounded-[18px] border border-[#f2d79f26] bg-[linear-gradient(180deg,#f2d79f_0%,#d8af66_48%,#a97531_100%)] px-5 text-center font-heading text-[0.84rem] uppercase tracking-[0.12em] text-night shadow-[0_14px_30px_rgba(169,117,49,0.24)] transition hover:brightness-105"
                                >
                                    <ExternalLink size={16} />
                                    <span>Open in Google Maps</span>
                                </a>

                                <a
                                    href={GOOGLE_MAPS_DIRECTIONS_URL}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="site-footer-primary-action inline-flex min-h-[56px] items-center justify-center gap-2 rounded-[18px] border border-[#c6934b42] bg-[rgba(255,255,255,0.03)] px-5 text-center font-heading text-[0.84rem] uppercase tracking-[0.12em] text-ivory shadow-[inset_0_1px_0_rgba(255,238,208,0.04)] transition hover:border-[#e3c07a] hover:text-[#fff1cf]"
                                >
                                    <Navigation size={16} />
                                    <span>Get Directions</span>
                                </a>
                            </div>
                        </div>

                        <div className="grid gap-4 lg:pl-8">
                            <div className="site-footer-info-grid grid gap-4 text-sm text-smoke sm:grid-cols-2">
                                <div className="site-footer-info-card rounded-[22px] border border-[#c6934b22] bg-[rgba(255,255,255,0.03)] px-5 py-5">
                                    <div className="flex items-start gap-3">
                                        <Phone size={18} className="site-footer-info-icon mt-1 shrink-0 text-white" />
                                        <div className="min-w-0">
                                            <p className="site-footer-info-label text-[10px] uppercase tracking-[0.22em] text-white">
                                                Phone
                                            </p>
                                            <p className="site-footer-info-value mt-3 text-sm leading-7 text-ivory">
                                                {info?.phone || '+353 00 000 0000'}
                                            </p>
                                        </div>
                                    </div>
                                </div>

                                <div className="site-footer-info-card rounded-[22px] border border-[#c6934b22] bg-[rgba(255,255,255,0.03)] px-5 py-5">
                                    <div className="flex items-start gap-3">
                                        <MapPin size={18} className="site-footer-info-icon mt-1 shrink-0 text-white" />
                                        <div className="min-w-0">
                                            <p className="site-footer-info-label text-[10px] uppercase tracking-[0.22em] text-white">
                                                Location
                                            </p>
                                            <p className="site-footer-info-value mt-3 text-sm leading-7 text-ivory">
                                                {info?.address || 'Royal Chair Barber and Beauty Salon, Ireland'}
                                            </p>
                                        </div>
                                    </div>
                                </div>

                                <div className="site-footer-info-card rounded-[22px] border border-[#c6934b22] bg-[rgba(255,255,255,0.03)] px-5 py-5 sm:col-span-2">
                                    <div className="flex items-start gap-3">
                                        <Clock size={18} className="site-footer-info-icon mt-1 shrink-0 text-white" />
                                        <div className="min-w-0">
                                            <p className="site-footer-info-label text-[10px] uppercase tracking-[0.22em] text-white">
                                                Hours
                                            </p>
                                            <p className="site-footer-info-value mt-3 text-sm leading-7 text-ivory">
                                                {hoursText || 'Mon-Fri: 9:00 AM - 5:00 PM | Sat: 10:00 AM - 6:00 PM'}
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="mt-8 flex flex-col items-center gap-6">
                    <div className="footer-socials">
                        <a
                            href="https://www.instagram.com/royalchair.ie"
                            target="_blank"
                            rel="noopener noreferrer"
                            className="footer-social-link"
                            aria-label="Royal Chair on Instagram"
                            title="Instagram"
                        >
                            <FaInstagram size={18} />
                        </a>

                        <a
                            href="https://www.tiktok.com/@royalchair.ie"
                            target="_blank"
                            rel="noopener noreferrer"
                            className="footer-social-link"
                            aria-label="Royal Chair on TikTok"
                            title="TikTok"
                        >
                            <FaTiktok size={18} />
                        </a>
                    </div>

                    <div className="divider" />

                    <div className="site-footer-links flex flex-wrap items-center justify-center gap-4 text-center text-[12px] uppercase tracking-[0.18em] text-smoke">
                        <SectionLink fallbackPath="/" sectionId={null}>
                            Home
                        </SectionLink>
                        <a
                            href="/services"
                            onClick={(event) => {
                                event.preventDefault();
                                navigateToRouteTop('/services');
                            }}
                        >
                            Services
                        </a>
                        <a
                            href="/booking"
                            onClick={(event) => {
                                event.preventDefault();
                                navigateToRouteTop('/booking');
                            }}
                        >
                            Booking
                        </a>
                        <SectionLink sectionId="employees">Barbers</SectionLink>
                        <Link to="/faq">FAQ</Link>
                    </div>

                    <div className="ornament" />

                    <div className="footer-credit">
                        Copyright {new Date().getFullYear()} Royal Chair Barber and Beauty Salon. All rights reserved.
                        <br />
                        Website design & development by <span className="footer-author">Yehor Kachur</span>.
                    </div>
                </div>
            </div>
        </footer>
    );
}
