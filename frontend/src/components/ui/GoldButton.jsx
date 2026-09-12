export default function GoldButton({ children, className = '', ...rest }) {
    return (
        <button
            type={rest.type || 'button'}
            className={`btn-gold ${className}`}
            {...rest}
        >
            {children}
        </button>
    );
}
