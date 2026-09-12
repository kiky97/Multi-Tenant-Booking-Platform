import { Moon, SunMedium } from 'lucide-react';
import { useColorScheme } from '../../context/ColorSchemeContext';

export default function AdminColorSchemeToggle({ className = '' }) {
    const { isDarkMode, toggleColorScheme } = useColorScheme();
    const Icon = isDarkMode ? SunMedium : Moon;
    const buttonLabel = isDarkMode ? 'Switch to light mode' : 'Switch to dark mode';

    return (
        <button
            type="button"
            className={`admin-theme-toggle ${className}`.trim()}
            onClick={toggleColorScheme}
            aria-label={buttonLabel}
            title={buttonLabel}
        >
            <Icon size={18} strokeWidth={1.9} />
        </button>
    );
}
