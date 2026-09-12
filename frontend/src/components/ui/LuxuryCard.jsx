export default function LuxuryCard({ children, className = '' }) {
    return (
        <div className={`glass-card gold-border shadow-card ${className}`}>
            {children}
        </div>
    );
}
