export default function SectionTitle({ title, subtitle }) {
    return (
        <div className="text-center mb-6">
            <p className="lux-subtitle">{subtitle}</p>
            <h2 className="lux-section-title">{title}</h2>
            <div className="divider" />
        </div>
    );
}
