import { defineConfig } from 'astro/config';
import tailwind from '@astrojs/tailwind';
import sitemap from '@astrojs/sitemap';

export default defineConfig({
  output: 'static',
  site: process.env.PUBLIC_SITE_URL || 'http://localhost:4321',
  integrations: [
    tailwind(),
    sitemap({
      filter: (page) => !page.includes('/contact/thanks'),
    }),
  ],
  vite: {
    build: { target: 'es2022' },
  },
});
