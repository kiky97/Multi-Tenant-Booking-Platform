/** @type {import('tailwindcss').Config} */
export default {
    content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
    theme: {
        extend: {
            fontFamily: {
                heading: ['system-ui', '-apple-system', 'BlinkMacSystemFont', '"Segoe UI"', 'sans-serif'],
                body: ['system-ui', '-apple-system', 'BlinkMacSystemFont', '"Segoe UI"', 'sans-serif']
            },
            colors: {
                night: '#0b0806',
                ember: '#1a120c',
                gold: '#c6934b',
                goldBright: '#f3c97a',
                bronze: '#8c5a26',
                ivory: '#f1e3c3',
                smoke: '#b9a588',
                card: '#120d0a'
            },
            boxShadow: {
                gold: '0 0 20px rgba(243, 201, 122, 0.35)',
                'inner-gold': 'inset 0 0 18px rgba(198, 147, 75, 0.5)',
                card: '0 10px 40px rgba(0,0,0,0.35)'
            },
            backgroundImage: {
                'luxury-noise':
                    "radial-gradient(ellipse at 20% 20%, rgba(243,201,122,0.08), transparent 40%), radial-gradient(ellipse at 80% 0%, rgba(140,90,38,0.12), transparent 45%), radial-gradient(ellipse at 10% 80%, rgba(140,90,38,0.12), transparent 45%), radial-gradient(ellipse at 90% 70%, rgba(243,201,122,0.08), transparent 40%), linear-gradient(180deg, rgba(12,9,7,0.95), rgba(16,12,10,0.95))",
                'luxury-grain':
                    "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160' viewBox='0 0 160 160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.75' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='.18'/%3E%3C/svg%3E\")"
            },
            borderRadius: {
                smooth: '18px'
            }
        }
    },
    plugins: []
};
