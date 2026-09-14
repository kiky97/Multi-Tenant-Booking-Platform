import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import GlobalErrorBoundary from './components/GlobalErrorBoundary';
import { ColorSchemeProvider } from './context/ColorSchemeContext';
import './index.css';
import { primeThemeLogoAssets } from './utils/themeAssets';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

primeThemeLogoAssets();

const root = document.getElementById('root');
if (!root) throw new Error('Root element is missing');

createRoot(root).render(
  <GlobalErrorBoundary>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ColorSchemeProvider>
          <App />
        </ColorSchemeProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </GlobalErrorBoundary>,
);
