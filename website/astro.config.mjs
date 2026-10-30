import { defineConfig } from 'astro/config';
import tailwind from '@astrojs/tailwind';

export default defineConfig({
  output: 'static',
  site: process.env.PUBLIC_SITE_URL || 'http://localhost:4321',
  integrations: [tailwind()],
  vite: {
    build: { target: 'es2022' },
  },
});
