import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import GlobalErrorBoundary from './components/GlobalErrorBoundary';
import { ColorSchemeProvider } from './context/ColorSchemeContext';
import './index.css';
import { primeThemeLogoAssets } from './utils/themeAssets';

primeThemeLogoAssets();

createRoot(document.getElementById('root')).render(
    <GlobalErrorBoundary>
        <BrowserRouter>
            <ColorSchemeProvider>
                <App />
            </ColorSchemeProvider>
        </BrowserRouter>
    </GlobalErrorBoundary>
);
