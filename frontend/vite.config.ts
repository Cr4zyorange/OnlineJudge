import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080'
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
