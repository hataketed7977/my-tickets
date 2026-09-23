import path from 'node:path';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@api': path.resolve(import.meta.dirname, 'src/api'),
      '@auth': path.resolve(import.meta.dirname, 'src/auth'),
      '@components': path.resolve(import.meta.dirname, 'src/components'),
      '@hooks': path.resolve(import.meta.dirname, 'src/hooks'),
      '@lib': path.resolve(import.meta.dirname, 'src/lib'),
      '@pages': path.resolve(import.meta.dirname, 'src/pages'),
      '@types': path.resolve(import.meta.dirname, 'src/types'),
      '@': path.resolve(import.meta.dirname, 'src'),
    },
  },
  server: {
    host: '127.0.0.1',
    port: Number(process.env.WEB_PORT ?? 51888),
    strictPort: true,
    proxy: {
      '/api': {
        target: `http://localhost:${process.env.API_PORT ?? 15588}`,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
