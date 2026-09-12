import { useNavigate } from 'react-router-dom';
import { preparePendingNavigation } from '../utils/navigation';
import useHairSalonInfo from '../hooks/useHairSalonInfo';

export default function Hero() {
    const info = useHairSalonInfo();
    const navigate = useNavigate();
    const phoneNumber = info?.phone?.trim() || '+35383 182 3456';
    const description =
        info?.description?.trim() ||
        'Cuts, styling, coloring, and grooming for women, men, and kids - from quick trims to bold color and restorative treatments in one welcoming space.';
    const telHref = `tel:${phoneNumber.replace(/[^+0-9]/g, '')}`;

    return (
        <section
            id="hero"
            className="hero-section relative overflow-hidden bg-black min-h-[80vh]"
        >
            <video
                className="hero-media absolute inset-y-0 left-1/2 -translate-x-1/2 w-[100%] h-full object-cover bg-black"
                poster="/hero-bg-poster.webp"
                crossOrigin="anonymous"
                autoPlay
                loop
                muted
                playsInline
                aria-hidden="true"
            >
                <source media="(max-width: 767px)" src="/hero-bg-mobile.mp4" type="video/mp4" />
                <source src="/hero-bg.mp4" type="video/mp4" />
            </video>
            <div className="hero-overlay absolute inset-0" />
            <div className="hero-grain absolute inset-0 pointer-events-none" aria-hidden="true" />
            <div className="services-page-shell hero-shell pt-16 pb-14">
                <div className="hero-content mx-auto flex w-full max-w-[980px] flex-col items-center justify-center text-center">
                    <div className="hero-copy-shell">
                        <p className="hero-kicker">
                            Royal Chair Barber & Beauty Salon
                        </p>

                        <p className="hero-eyebrow text-sm tracking-[0.32em] text-white uppercase">
                            Women / Men / Kids
                        </p>

                        <h1 className="hero-title text-4xl sm:text-5xl font-heading text-ivory leading-tight">
                            Your Style, Perfected
                        </h1>

                        <p className="hero-description">
                            {description}
                        </p>

                        <div className="hero-meta">
                            <a
                                className="hero-phone text-sm text-smoke tracking-[0.14em]"
                                href={telHref}
                            >
                                {phoneNumber}
                            </a>
                        </div>

                        <div className="hero-actions">
                            <button
                                type="button"
                                className="btn-gold"
                                onClick={() => {
                                    preparePendingNavigation('/booking', null);
                                    navigate('/booking');
                                }}
                            >
                                Book Appointment
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </section>
    );
}
