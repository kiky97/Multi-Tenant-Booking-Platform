const COLOR_SCHEME_STORAGE_KEY = 'royal-chair-color-scheme';
const DEFAULT_COLOR_SCHEME = 'dark';
const THEME_LOGO_PATHS = ['/logo-royal-l.jpg', '/logo-royal-d.jpg'];
let themeLogoAssetsPrimed = false;

function primeThemeLogo(src) {
    if (!src || typeof Image === 'undefined') {
        return;
    }

    const image = new Image();
    image.decoding = 'async';
    image.src = src;
}

function normalizeColorScheme(value) {
    return value === 'light' || value === 'dark' ? value : DEFAULT_COLOR_SCHEME;
}

export function resolveStoredColorScheme() {
    if (typeof window === 'undefined') {
        return DEFAULT_COLOR_SCHEME;
    }

    try {
        const storedColorScheme = window.localStorage.getItem(COLOR_SCHEME_STORAGE_KEY);

        if (storedColorScheme) {
            return normalizeColorScheme(storedColorScheme);
        }
    } catch {
        // Ignore storage access failures and fall back to the document/default state.
    }

    if (typeof document !== 'undefined') {
        return normalizeColorScheme(document.documentElement.dataset.theme);
    }

    return DEFAULT_COLOR_SCHEME;
}

export function getThemeLogoPath(colorScheme) {
    return normalizeColorScheme(colorScheme) === 'light'
        ? '/logo-royal-l.jpg'
        : '/logo-royal-d.jpg';
}

function getThemeFaviconPath(colorScheme) {
    return getThemeLogoPath(colorScheme);
}

export function primeThemeLogoAssets() {
    if (themeLogoAssetsPrimed || typeof Image === 'undefined') {
        return;
    }

    themeLogoAssetsPrimed = true;
    const currentLogoPath = getThemeLogoPath(resolveStoredColorScheme());
    const alternateLogoPath = THEME_LOGO_PATHS.find((src) => src !== currentLogoPath);

    primeThemeLogo(currentLogoPath);

    if (!alternateLogoPath || typeof window === 'undefined') {
        return;
    }

    const primeAlternateLogo = () => {
        primeThemeLogo(alternateLogoPath);
    };

    const scheduleAlternateLogoPriming = () => {
        if (typeof window.requestIdleCallback === 'function') {
            window.requestIdleCallback(primeAlternateLogo, { timeout: 1500 });
            return;
        }

        window.setTimeout(primeAlternateLogo, 1500);
    };

    if (document.readyState === 'complete') {
        scheduleAlternateLogoPriming();
        return;
    }

    if (typeof window.requestIdleCallback === 'function') {
        window.addEventListener('load', scheduleAlternateLogoPriming, { once: true });
    } else {
        window.addEventListener('load', scheduleAlternateLogoPriming, { once: true });
    }
}

function syncThemeFavicon(colorScheme) {
    if (typeof document === 'undefined') {
        return;
    }

    const faviconHref = getThemeFaviconPath(colorScheme);
    let faviconLink =
        document.querySelector('link[data-royal-favicon="true"]') ||
        document.querySelector('link[rel="icon"]');

    if (!faviconLink) {
        faviconLink = document.createElement('link');
        document.head.appendChild(faviconLink);
    }

    faviconLink.setAttribute('data-royal-favicon', 'true');
    faviconLink.setAttribute('rel', 'icon');
    faviconLink.setAttribute('type', 'image/jpeg');
    faviconLink.setAttribute('href', faviconHref);
}

export function applyColorSchemeToDocument(colorScheme) {
    if (typeof document === 'undefined') {
        return normalizeColorScheme(colorScheme);
    }

    const normalizedColorScheme = normalizeColorScheme(colorScheme);

    document.documentElement.dataset.theme = normalizedColorScheme;
    document.documentElement.style.colorScheme = normalizedColorScheme;
    document.body?.setAttribute('data-theme', normalizedColorScheme);
    syncThemeFavicon(normalizedColorScheme);

    return normalizedColorScheme;
}
