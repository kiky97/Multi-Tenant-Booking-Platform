import { useLayoutEffect, useRef, useState } from 'react';
import { ChevronDown, Scissors, Clock3, MapPin } from 'lucide-react';
import '../styles/faq.css';

const faqItems = [
    {
        question: 'What are your opening hours?',
        answer:
            'Our opening hours may vary by day. You can find the latest schedule in the contact section or when booking your appointment online.'
    },
    {
        question: 'Do I need to make an appointment?',
        answer:
            'We strongly recommend booking in advance to secure your preferred barber, service, and time slot.'
    },
    {
        question: 'What services do you offer?',
        answer:
            'We offer a curated range of premium grooming services including haircuts, beard grooming, detailing, and selected specialty treatments.'
    },
    {
        question: 'How long does a haircut or beard grooming appointment take?',
        answer:
            'The appointment length depends on the selected service. Most sessions typically take between 20 and 60 minutes.'
    },
    {
        question: 'Do you offer any special packages?',
        answer:
            'Yes, selected service combinations and premium grooming packages may be available depending on the season and current offer.'
    },
    {
        question: 'What should I do if I need to cancel or reschedule my booking?',
        answer:
            'Please cancel or reschedule as early as possible so we can make the slot available for another client.'
    },
    {
        question: 'Are walk-ins welcome?',
        answer:
            'Walk-ins may be possible depending on availability, but appointments are recommended for the best experience.'
    },
    {
        question: 'What payment methods do you accept?',
        answer:
            'We accept standard payment methods available at the barbershop and through our booking flow.'
    },
    {
        question: 'Where is the barbershop located?',
        answer:
            'Our barbershop location is listed in the contact section of the website together with the phone number and opening hours.'
    },
    {
        question: 'Do you provide aftercare advice for beard grooming?',
        answer:
            'Yes, we are happy to provide aftercare guidance and product recommendations to help you maintain the best results after your appointment.'
    }
];

function FaqItem({ item, isOpen, onToggle }) {
    const contentRef = useRef(null);
    const [height, setHeight] = useState(0);

    useLayoutEffect(() => {
        if (!contentRef.current) return;

        if (isOpen) {
            setHeight(contentRef.current.scrollHeight);
        } else {
            setHeight(0);
        }
    }, [isOpen, item.answer]);

    return (
        <div className="faq-card">
            <button type="button" className="faq-trigger" onClick={onToggle} aria-expanded={isOpen}>
                <span className="faq-question">{item.question}</span>
                <ChevronDown
                    size={20}
                    className={`faq-chevron ${isOpen ? 'faq-chevron-open' : ''}`}
                />
            </button>

            <div
                className="faq-answer-wrap"
                style={{
                    height: `${height}px`,
                    opacity: isOpen ? 1 : 0
                }}
            >
                <div ref={contentRef} className="faq-answer">
                    {item.answer}
                </div>
            </div>
        </div>
    );
}

export default function Faq() {
    const [openIndex, setOpenIndex] = useState(0);

    return (
        <section className="services-page-shell py-10 sm:py-12 lg:py-14">
            <div className="faq-hero">
                <p className="faq-eyebrow">Royal Chair Barber & Beauty Salon</p>

                <h1 className="faq-title">FAQ</h1>

                <p className="faq-description">
                    We understand that you may have questions about our services and grooming
                    techniques. This section aims to provide answers to the frequently asked
                    questions to ensure that you have all the information you need before booking
                    an appointment with us.
                </p>

                <div className="ornament !mt-5 !w-[150px] sm:!w-[190px]" />

                <div className="faq-badges">
                    <div className="faq-badge">
                        <Scissors size={16} />
                        <span>Premium Grooming</span>
                    </div>
                    <div className="faq-badge">
                        <Clock3 size={16} />
                        <span>Booking Guidance</span>
                    </div>
                    <div className="faq-badge">
                        <MapPin size={16} />
                        <span>Visit Information</span>
                    </div>
                </div>
            </div>

            <div className="faq-section-head">
                <p className="lux-subtitle">Everything you may want to know</p>
                <h2 className="lux-section-title">Frequently asked questions</h2>
                <div className="divider" />
            </div>

            <div className="faq-list">
                {faqItems.map((item, index) => (
                    <FaqItem
                        key={item.question}
                        item={item}
                        isOpen={openIndex === index}
                        onToggle={() => setOpenIndex(openIndex === index ? -1 : index)}
                    />
                ))}
            </div>
        </section>
    );
}
