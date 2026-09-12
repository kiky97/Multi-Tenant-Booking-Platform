import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { publicApi } from '../api/publicApi';
import SectionTitle from './ui/SectionTitle';
import LuxuryCard from './ui/LuxuryCard';
import GoldButton from './ui/GoldButton';
import '../styles/services.css';

const FALLBACK_IMAGE =
    'https://images.unsplash.com/photo-1621605815971-fbc98d665033?auto=format&fit=crop&w=900&q=80';

export default function ServicesSection({
    showViewAllButton = true,
    title = 'Our Services',
    subtitle = 'crafted cuts & shaves',
    wide = false,
    limit = null,
    sectionId = 'services'
}) {
    const [services, setServices] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const navigate = useNavigate();

    useEffect(() => {
        let ignore = false;

        async function loadTreatments() {
            setLoading(true);
            setError('');

            try {
                const data = await publicApi.getTreatments();

                if (!ignore) {
                    setServices(Array.isArray(data) ? data : []);
                }
            } catch (err) {
                if (!ignore) {
                    setError(err.message || 'Failed to load services');
                    setServices([]);
                }
            } finally {
                if (!ignore) setLoading(false);
            }
        }

        loadTreatments();

        return () => {
            ignore = true;
        };
    }, []);

    const sortedServices = useMemo(
        () => [...services].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)),
        [services]
    );

    const visibleServices = useMemo(() => {
        if (typeof limit === 'number') {
            return sortedServices.slice(0, limit);
        }
        return sortedServices;
    }, [sortedServices, limit]);

    const sectionClass = 'services-page-shell py-8';
    const gridClass = wide
        ? 'services-grid'
        : 'grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3';

    return (
        <section className={sectionClass} id={sectionId || undefined}>
            <SectionTitle title={title} subtitle={subtitle} />

            {loading && <div className="text-center text-ivory/80">Loading services...</div>}
            {error && <div className="text-center text-red-300">{error}</div>}

            {!loading && !error && visibleServices.length === 0 && (
                <div className="text-center text-ivory/70">No services available.</div>
            )}

            {!loading && !error && visibleServices.length > 0 && (
                <div className={gridClass}>
                    {visibleServices.map((service, idx) => (
                        <div
                            key={service.id}
                            className={wide ? 'service-card h-full' : 'h-full'}
                        >
                            <LuxuryCard className="h-full overflow-hidden rounded-[20px] border border-[#c6934b45] bg-[linear-gradient(180deg,rgba(12,12,12,0.96),rgba(4,4,4,0.94))]">
                                <div className={wide ? 'service-card-inner' : 'flex flex-col'}>
                                    <div
                                        className={wide ? 'service-card-image' : 'relative aspect-[4/3] w-full overflow-hidden bg-[rgba(7,7,7,0.95)]'}
                                    >
                                        <img
                                            src={service.photoUrl || FALLBACK_IMAGE}
                                            alt={service.name ? `${service.name} service` : 'Salon service'}
                                            loading={wide && idx < 2 ? 'eager' : 'lazy'}
                                            decoding="async"
                                            fetchPriority={wide && idx === 0 ? 'high' : 'auto'}
                                            className="h-full w-full object-cover"
                                        />
                                        <div
                                            className="absolute inset-0"
                                            aria-hidden="true"
                                            style={{
                                                background:
                                                    'linear-gradient(180deg, rgba(12,9,7,0.10), rgba(12,9,7,0.52))'
                                            }}
                                        />
                                    </div>

                                    <div className={wide ? 'service-card-content' : 'flex flex-col gap-4 p-5 sm:p-6'}>
                                        <div className="min-w-0 text-center">
                                            <h3
                                                className={
                                                    wide
                                                        ? 'service-card-title'
                                                        : 'font-heading text-[1rem] leading-[1.45] tracking-[0.14em] uppercase text-ivory sm:text-[1.08rem]'
                                                }
                                            >
                                                {service.name}
                                            </h3>
                                        </div>

                                        {wide ? (
                                            <p className="text-center text-sm leading-7 text-smoke">
                                                {service.description}
                                            </p>
                                        ) : null}

                                        <div className="ornament !mt-0 !w-[72%]" />

                                        <div
                                            className={
                                                wide
                                                    ? 'service-card-meta'
                                                    : 'flex items-center justify-between gap-4 rounded-[14px] border border-[#c6934b22] bg-[rgba(255,255,255,0.02)] px-4 py-3'
                                            }
                                        >
                                            <div className={wide ? 'service-card-meta-block' : 'flex flex-col'}>
                                                <span className={wide ? 'service-card-meta-label' : 'text-[10px] uppercase tracking-[0.22em] text-smoke'}>
                                                    Duration
                                                </span>
                                                <span
                                                    className={
                                                        wide
                                                            ? 'service-card-meta-value'
                                                            : 'font-heading text-sm tracking-[0.08em] text-ivory sm:text-base'
                                                    }
                                                >
                                                    {service.durationMinutes} min
                                                </span>
                                            </div>

                                            {wide ? null : (
                                                <div className="h-8 w-px bg-gradient-to-b from-transparent via-[#c6934b55] to-transparent" />
                                            )}

                                            <div
                                                className={
                                                    wide ? 'service-card-meta-block' : 'flex flex-col items-end'
                                                }
                                            >
                                                <span className={wide ? 'service-card-meta-label' : 'text-[10px] uppercase tracking-[0.22em] text-smoke'}>
                                                    Price
                                                </span>
                                                <span
                                                    className={
                                                        wide
                                                            ? 'service-card-meta-value service-card-price'
                                                            : 'font-heading text-base tracking-[0.08em] text-white sm:text-[1.1rem]'
                                                    }
                                                >
                                                    &euro;{service.price}
                                                </span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </LuxuryCard>
                        </div>
                    ))}
                </div>
            )}

            {showViewAllButton && !loading && !error && sortedServices.length > 0 ? (
                <div className="mt-8 flex justify-center">
                    <GoldButton onClick={() => navigate('/services')}>View All Services</GoldButton>
                </div>
            ) : null}
        </section>
    );
}
