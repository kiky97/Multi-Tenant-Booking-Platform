import {
    createContext,
    useCallback,
    useContext,
    useEffect,
    useLayoutEffect,
    useMemo,
    useState
} from 'react';
import { applyColorSchemeToDocument, resolveStoredColorScheme } from '../utils/themeAssets';

const COLOR_SCHEME_STORAGE_KEY = 'royal-chair-color-scheme';

const ColorSchemeContext = createContext(null);

if (typeof document !== 'undefined') {
    applyColorSchemeToDocument(resolveStoredColorScheme());
}

export function ColorSchemeProvider({ children }) {
    const [colorScheme, setColorScheme] = useState(() => resolveStoredColorScheme());

    useLayoutEffect(() => {
        applyColorSchemeToDocument(colorScheme);
    }, [colorScheme]);

    useEffect(() => {
        if (typeof window === 'undefined') {
            return;
        }

        window.localStorage.setItem(COLOR_SCHEME_STORAGE_KEY, colorScheme);
    }, [colorScheme]);

    const toggleColorScheme = useCallback(() => {
        setColorScheme((currentColorScheme) =>
            currentColorScheme === 'dark' ? 'light' : 'dark'
        );
    }, []);

    const value = useMemo(
        () => ({
            colorScheme,
            isDarkMode: colorScheme === 'dark',
            setColorScheme,
            toggleColorScheme
        }),
        [colorScheme, toggleColorScheme]
    );

    return (
        <ColorSchemeContext.Provider value={value}>
            {children}
        </ColorSchemeContext.Provider>
    );
}

export function useColorScheme() {
    const contextValue = useContext(ColorSchemeContext);

    if (!contextValue) {
        throw new Error('useColorScheme must be used inside ColorSchemeProvider.');
    }

    return contextValue;
}
