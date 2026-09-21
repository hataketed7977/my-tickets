import path from 'node:path';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, 'src'),
    },
  },
  server: {
    host: 'localhost',
    port: Number(process.env.WEB_PORT ?? 51888),
    strictPort: true,
    proxy: {
      '/api': {
        target: `http://localhost:${process.env.API_PORT ?? 55888}`,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
