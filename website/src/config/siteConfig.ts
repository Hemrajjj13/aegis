// Central site configuration for marketing website.
// Zero hardcoded localhost - resolves from env with fallback.

export const siteConfig = {
  productName: 'Aegis',
  tagline: 'Change fraud rules with confidence by testing them against your history before you ship.',
  description:
    'A real-time, deterministic transaction decision engine and backtest platform for payment operators.',
  consoleUrl: (import.meta.env.PUBLIC_CONSOLE_URL || 'http://localhost:5173').replace(/\/$/, ''),
  siteUrl: (import.meta.env.PUBLIC_SITE_URL || 'http://localhost:4321').replace(/\/$/, ''),
  apiUrl: (import.meta.env.PUBLIC_API_URL || 'http://localhost:8080').replace(/\/$/, ''),
  securityEmail: import.meta.env.PUBLIC_SECURITY_EMAIL || 'security@example.com',
  legalName: 'Project Aegis Systems',
  year: new Date().getFullYear(),
};
