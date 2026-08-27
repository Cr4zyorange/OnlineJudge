import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET?.trim() || 'http://127.0.0.1:8080';

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': apiProxyTarget
    }
  },
  build: {
    rollupOptions: {
      input: {
        main: 'index.html',
        courses: 'courses/index.html'
      }
    }
  },
  test: {
    environment: 'jsdom',
    include: [
      'src/**/*.spec.ts',
      'tests/unit/**/*.spec.ts'
    ]
  }
});
