import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import { fileURLToPath } from 'url';

const root = fileURLToPath(new URL('.', import.meta.url));

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, root, '');
    const apiProxyTarget = env.VITE_DEV_API_PROXY_TARGET?.trim();
    const allowedHosts = (env.VITE_DEV_ALLOWED_HOSTS || '')
        .split(',')
        .map((host) => host.trim())
        .filter(Boolean);

    return {
        plugins: [react()],
        server: {
            host: '0.0.0.0',
            port: 5173,
            allowedHosts: allowedHosts.length > 0 ? allowedHosts : undefined,
            proxy: apiProxyTarget
                ? {
                    '/api': {
                        target: apiProxyTarget,
                        changeOrigin: true,
                        secure: false
                    }
                }
                : undefined
        },
        build: {
            sourcemap: false,
            minify: 'esbuild',
            cssCodeSplit: true,
            rollupOptions: {
                input: {
                    main: resolve(root, 'index.html'),
                    admin: resolve(root, 'admin.html')
                },
                output: {
                    manualChunks(id) {
                        if (
                            id.includes('node_modules/react/')
                            || id.includes('node_modules/react-dom/')
                            || id.includes('node_modules/react-router-dom/')
                            || id.includes('node_modules/@remix-run/router/')
                            || id.includes('node_modules/scheduler/')
                        ) {
                            return 'react-vendor';
                        }
                        return undefined;
                    }
                }
            }
        }
    };
});
