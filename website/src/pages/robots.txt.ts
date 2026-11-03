import type { APIRoute } from 'astro';
import { siteConfig } from '../config/siteConfig';

export const GET: APIRoute = () => {
  const disallowAll = import.meta.env.PUBLIC_INDEXABLE === 'false';
  const siteUrl = siteConfig.siteUrl.replace(/\/$/, '');
  const body = disallowAll
    ? `User-agent: *\nDisallow: /\n\nSitemap: ${siteUrl}/sitemap-index.xml\n`
    : `User-agent: *\nAllow: /\nDisallow: /contact/thanks\nDisallow: /contact/error\n\nSitemap: ${siteUrl}/sitemap-index.xml\n`;

  return new Response(body, {
    headers: { 'Content-Type': 'text/plain; charset=utf-8' },
  });
};
