import ServicesSection from '../components/ServicesSection';

export default function Services() {
    return (
        <section className="pt-10 sm:pt-12 lg:pt-14">
            <div className="services-page-shell">
                <div className="services-intro">
                    <p className="mb-3 text-[11px] uppercase tracking-[0.3em] text-goldBright/90 sm:text-xs">
                        Royal Chair Barber & Beauty Salon
                    </p>

                    <h1 className="font-heading text-[2rem] uppercase tracking-[0.12em] text-goldBright sm:text-[2.4rem] lg:text-[2.8rem]">
                        Haircuts, Beard Trims & Beauty Services in Ennis
                    </h1>

                    <p className="services-intro-text">
                        Discover our full range of grooming services, carefully tailored to keep your style
                        sharp, refined, and effortlessly polished.
                    </p>

                    <div className="ornament !mt-5 !w-[140px] sm:!w-[180px]" />
                </div>
            </div>

            <ServicesSection
                showViewAllButton={false}
                title="Explore Our Treatments"
                subtitle="premium grooming for every detail"
                wide
            />
        </section>
    );
}
