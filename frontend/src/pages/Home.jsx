import { lazy } from 'react';
import DeferredSection from '../components/DeferredSection';
import Hero from '../components/Hero';

const ServicesSection = lazy(() => import('../components/ServicesSection'));
const EmployeesSection = lazy(() => import('../components/EmployeesSection'));
const GallerySection = lazy(() => import('../components/GallerySection'));

export default function Home() {
    return (
        <div id="top">
            <Hero />
            <DeferredSection
                sectionId="services"
                placeholderMinHeight={420}
                rootMargin="0px 0px 80px 0px"
            >
                <ServicesSection limit={3} sectionId={null} />
            </DeferredSection>
            <DeferredSection
                sectionId="employees"
                placeholderMinHeight={820}
                rootMargin="0px 0px 120px 0px"
            >
                <EmployeesSection sectionId={null} />
            </DeferredSection>
            <DeferredSection
                sectionId="gallery"
                placeholderMinHeight={760}
                rootMargin="0px 0px 180px 0px"
            >
                <GallerySection sectionId={null} />
            </DeferredSection>
        </div>
    );
}
