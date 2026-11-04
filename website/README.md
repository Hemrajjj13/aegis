# Aegis Public Website

Static marketing website for Project Aegis, built with [Astro](https://astro.build).

## Key Principles & Invariants

1. **Zero Client-Side JavaScript:** All pages render statically to pure HTML and CSS. No hydration scripts, no external tracking tags, no client-side bundle.
2. **Zero Cookies:** The marketing site sets no cookies, reads no cookies, and uses no third-party session tracking.
3. **Zero Third-Party Network Requests:** Fonts are self-hosted via `@fontsource/inter` and `@fontsource/jetbrains-mono`. Icons and diagrams are inlined SVGs.
4. **Form Handling via Backend:** The contact form is a native `<form method="POST">` submitted to the Spring Boot backend (`POST /v1/public/contact`), which enforces rate limiting, honeypot spam defenses, CR/LF sanitization, and email dispatching via Resend or local dev mailbox.

## Development & Build

```bash
# Install dependencies
npm install

# Start local dev server (default: http://localhost:4321)
npm run dev

# Content & banned phrase audit
npm run check:content

# Budget check (< 500 KB total HTML payload, 0 KB client JS)
npm run check:budget

# Production build
npm run build

# End-to-end Playwright tests
npm run test:e2e
```

## Environment Variables

| Variable | Default | Purpose |
| :--- | :--- | :--- |
| `PUBLIC_SITE_URL` | `http://localhost:4321` | Base canonical URL of the marketing website |
| `PUBLIC_CONSOLE_URL` | `http://localhost:5173` | URL of the Aegis console (Log in / Sign up links) |
| `PUBLIC_API_URL` | `http://localhost:8080` | URL of the Spring Boot decision and identity API |
| `PUBLIC_SECURITY_EMAIL` | `security@example.com` | Contact email displayed on the responsible disclosure page |

## Future Asset Storage (Cloudinary Guidance)

Currently, all assets are hosted locally or embedded directly as inline SVGs and optimized via Astro's built-in `<Image>` optimizer to maintain the zero third-party request invariant.

If Cloudinary or an external CDN is adopted in the future:
1. **Environment Variables:**
   - `CLOUDINARY_CLOUD_NAME`: The Cloudinary cloud account name.
   - `CLOUDINARY_API_KEY`: API key for server-side asset operations.
   - `CLOUDINARY_API_SECRET`: API secret (server-side only, never exposed to client).
   - `PUBLIC_CLOUDINARY_BASE_URL`: Base asset URL (e.g. `https://res.cloudinary.com/<cloud_name>/image/upload/`).
2. **Privacy & Architecture Disclosures:**
   - Cloudinary adds third-party network requests that transfer visitor IP addresses and browser headers to Cloudinary servers.
   - If enabled, this integration **must** be explicitly disclosed in `src/pages/privacy.astro` under Third-Party Service Providers.
   - Content Security Policy (CSP) headers in reverse proxies or CDN configs must be updated to whitelist `res.cloudinary.com`.

