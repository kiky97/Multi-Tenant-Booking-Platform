import { useEffect, useState } from 'react';
import useEmblaCarousel from 'embla-carousel-react';
import { useReducedMotion } from 'framer-motion';
import { ArrowLeft, ArrowRight } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import SectionTitle from './ui/SectionTitle';
import GoldButton from './ui/GoldButton';
import { preparePendingNavigation } from '../utils/navigation';
import { gallery } from '../data/gallery';
import '../styles/services.css';

const TWEEN_FACTOR_BASE = 0.26;

function clamp(value, min, max) {
    return Math.max(min, Math.min(value, max));
}

export default function GalleryShowcase({
    sectionId = 'gallery',
    className = 'services-page-shell py-8',
    title = 'Gallery',
    subtitle = 'Craft & Atmosphere',
    showBookingButton = true
}) {
    const navigate = useNavigate();
    const shouldReduceMotion = useReducedMotion();
    const [selectedIndex, setSelectedIndex] = useState(0);
    const [emblaRef, emblaApi] = useEmblaCarousel({
        align: 'center',
        containScroll: false,
        dragFree: false,
        duration: shouldReduceMotion ? 20 : 36,
        inViewThreshold: 0,
        loop: true,
        skipSnaps: false,
        slidesToScroll: 1
    });

    useEffect(() => {
        if (!emblaApi) {
            return undefined;
        }

        function setSelectedSlide() {
            setSelectedIndex(emblaApi.selectedScrollSnap());
        }

        function tweenSlides() {
            const engine = emblaApi.internalEngine();
            const scrollProgress = emblaApi.scrollProgress();
            const tweenFactor = TWEEN_FACTOR_BASE * emblaApi.scrollSnapList().length;

            emblaApi.scrollSnapList().forEach((scrollSnap, snapIndex) => {
                const slidesInSnap = engine.slideRegistry[snapIndex];

                slidesInSnap.forEach((slideIndex) => {

                    let diffToTarget = scrollSnap - scrollProgress;

                    if (engine.options.loop) {
                        engine.slideLooper.loopPoints.forEach((loopPoint) => {
                            const target = loopPoint.target();

                            if (slideIndex !== loopPoint.index || target === 0) {
                                return;
                            }

                            const sign = Math.sign(target);

                            if (sign === -1) {
                                diffToTarget = scrollSnap - (1 + scrollProgress);
                            }

                            if (sign === 1) {
                                diffToTarget = scrollSnap + (1 - scrollProgress);
                            }
                        });
                    }

                    const tweenValue = 1 - Math.abs(diffToTarget * tweenFactor);
                    const visibility = clamp(tweenValue, 0.18, 1);
                    const scale = clamp(0.62 + visibility * 0.38, 0.62, 1);
                    const opacity = clamp(0.22 + visibility * 0.78, 0.22, 1);
                    const blur = shouldReduceMotion ? 0 : clamp((1 - visibility) * 1.8, 0, 1.6);
                    const brightness = clamp(0.78 + visibility * 0.24, 0.78, 1.02);
                    const saturate = clamp(0.78 + visibility * 0.28, 0.78, 1.06);
                    const translateY = shouldReduceMotion ? 0 : (1 - visibility) * 18;
                    const slideNode = emblaApi.slideNodes()[slideIndex];

                    if (!slideNode) {
                        return;
                    }

                    slideNode.style.setProperty('--gallery-slide-scale', scale.toFixed(3));
                    slideNode.style.setProperty('--gallery-slide-opacity', opacity.toFixed(3));
                    slideNode.style.setProperty('--gallery-slide-blur', `${blur.toFixed(2)}px`);
                    slideNode.style.setProperty('--gallery-slide-brightness', brightness.toFixed(3));
                    slideNode.style.setProperty('--gallery-slide-saturate', saturate.toFixed(3));
                    slideNode.style.setProperty('--gallery-slide-translate-y', `${translateY.toFixed(2)}px`);
                    slideNode.style.zIndex = String(Math.round(visibility * 100));
                });
            });
        }

        function handleReInit() {
            setSelectedSlide();
            tweenSlides('reInit');
        }

        function handleSelect() {
            setSelectedSlide();
            tweenSlides('select');
        }

        function handleScroll() {
            tweenSlides('scroll');
        }

        handleReInit();

        emblaApi.on('reInit', handleReInit);
        emblaApi.on('select', handleSelect);
        emblaApi.on('scroll', handleScroll);
        emblaApi.on('slideFocus', handleSelect);

        return () => {
            emblaApi.off('reInit', handleReInit);
            emblaApi.off('select', handleSelect);
            emblaApi.off('scroll', handleScroll);
            emblaApi.off('slideFocus', handleSelect);
        };
    }, [emblaApi, shouldReduceMotion]);

    function showPreviousImage() {
        emblaApi?.scrollPrev();
    }

    function showNextImage() {
        emblaApi?.scrollNext();
    }

    function handleCarouselKeyDown(event) {
        if (event.key === 'ArrowLeft') {
            event.preventDefault();
            showPreviousImage();
        }

        if (event.key === 'ArrowRight') {
            event.preventDefault();
            showNextImage();
        }

        if (event.key === 'Home') {
            event.preventDefault();
            emblaApi?.scrollTo(0);
        }

        if (event.key === 'End') {
            event.preventDefault();
            emblaApi?.scrollTo(gallery.length - 1);
        }
    }

    return (
        <section className={className} id={sectionId}>
            <SectionTitle title={title} subtitle={subtitle} />

            <div className="gallery-carousel-breakout">
                <div
                    className="gallery-carousel-shell mt-8"
                    role="region"
                    aria-roledescription="carousel"
                    aria-label={`${title} gallery`}
                >
                    <p className="sr-only" aria-live="polite">
                        Showing gallery image {selectedIndex + 1} of {gallery.length}
                    </p>

                    <div
                        ref={emblaRef}
                        className="gallery-embla"
                        tabIndex={0}
                        onKeyDown={handleCarouselKeyDown}
                        aria-label="Gallery carousel viewport"
                    >
                        <div className="gallery-embla__container">
                            {gallery.map((item, index) => (
                                <button
                                    key={item.id}
                                    type="button"
                                    className={`gallery-embla__slide${index === selectedIndex ? ' is-selected' : ''}`}
                                    onClick={() => emblaApi?.scrollTo(index)}
                                    aria-label={
                                        index === selectedIndex
                                            ? `${item.alt}, current gallery image`
                                            : `Focus ${item.alt}`
                                    }
                                    aria-current={index === selectedIndex ? 'true' : undefined}
                                >
                                    <span className="gallery-embla__slide-card">
                                        <img
                                            src={item.src}
                                            alt={item.alt}
                                            loading={index < 3 ? 'eager' : 'lazy'}
                                            decoding="async"
                                            fetchPriority={index === 0 ? 'high' : 'low'}
                                            className="gallery-embla__image"
                                        />
                                    </span>
                                </button>
                            ))}
                        </div>
                    </div>

                    <div className="gallery-carousel-controls">
                        <button
                            type="button"
                            onClick={showPreviousImage}
                            className="gallery-carousel-nav"
                            aria-label="Show previous gallery image"
                        >
                            <ArrowLeft size={22} />
                        </button>

                        <button
                            type="button"
                            onClick={showNextImage}
                            className="gallery-carousel-nav"
                            aria-label="Show next gallery image"
                        >
                            <ArrowRight size={22} />
                        </button>
                    </div>
                </div>
            </div>

            {showBookingButton ? (
                <div className="mt-6 flex justify-center">
                    <GoldButton
                        onClick={() => {
                            preparePendingNavigation('/booking', null);
                            navigate('/booking');
                        }}
                    >
                        Book Appointment
                    </GoldButton>
                </div>
            ) : null}
        </section>
    );
}
