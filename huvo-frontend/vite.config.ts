import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Environment-driven configuration only (Huvo_Frontend_Context.md §5.2):
// no API host, port, or IP is ever hardcoded here — values come from
// .env.development / .env.<mode> files at build time.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    sourcemap: true,
    target: 'es2021',
  },
  server: {
    port: 5173,
  },
});
