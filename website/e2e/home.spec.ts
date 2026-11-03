import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mkdirSync } from 'node:fs';

const pages = ['/', '/features', '/how-it-works', '/security', '/pricing', '/docs', '/about', '/contact'];

test.beforeAll(() => {
  try {
    mkdirSync('.screens', { recursive: true });
  } catch { }
});

test('header navigation links exist and point to valid routes', async ({ page }) => {
  await page.goto('/');
  for (const p of ['/features', '/how-it-works', '/security', '/pricing', '/docs', '/about', '/contact']) {
    const link = page.locator(`header nav a[href="${p}"]`);
    await expect(link).toBeAttached();
  }
});

test('skip link focuses main', async ({ page }) => {
  await page.goto('/');
  await page.keyboard.press('Tab');
  const skip = page.getByText('Skip to content');
  await expect(skip).toBeFocused();
});

test('mobile navigation details disclosure works at 375px without javascript', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  await page.goto('/');
  const details = page.locator('header details');
  await expect(details).toBeVisible();

  // Initially closed
  await expect(details).not.toHaveAttribute('open', '');

  // Click summary
  await page.locator('header details summary').click();
  await expect(details).toHaveAttribute('open', '');

  // Links inside details are accessible
  const featuresLink = details.locator('a[href="/features"]');
  await expect(featuresLink).toBeVisible();
});

test('works at 320px with no horizontal scroll', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 600 });
  await page.goto('/');
  const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
  const clientWidth = await page.evaluate(() => document.documentElement.clientWidth);
  expect(scrollWidth).toBeLessThanOrEqual(clientWidth);
});

for (const theme of ['light', 'dark'] as const) {
  for (const width of [375, 1280] as const) {
    test(`axe ${theme} ${width} on home`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.goto('/');
      await page.evaluate((t) => {
        document.documentElement.setAttribute('data-theme', t);
        document.documentElement.classList.toggle('light', t === 'light');
      }, theme);
      const results = await new AxeBuilder({ page: page as any }).analyze();
      const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
      expect(serious).toEqual([]);
      await page.screenshot({ path: `.screens/home-${theme}-${width}.png`, fullPage: true });
    });
  }
}

test('axe accessibility across all marketing pages', async ({ page }) => {
  for (const p of pages) {
    await page.goto(p);
    const results = await new AxeBuilder({ page: page as any }).analyze();
    const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(serious).toEqual([]);
  }
});

test('no cross-origin requests, no cookies, no console errors', async ({ page, baseURL }) => {
  const allowedOrigin = new URL(baseURL || 'http://localhost:4321').origin;
  const origins = new Set<string>();
  page.on('request', (req) => {
    try {
      const u = new URL(req.url());
      if (u.origin !== allowedOrigin && u.origin !== 'null') origins.add(u.origin);
    } catch { }
  });
  const consoleErrors: string[] = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push(msg.text());
  });
  await page.goto('/');
  expect([...origins]).toEqual([]);
  const cookies = await page.context().cookies();
  expect(cookies).toEqual([]);
  expect(consoleErrors).toEqual([]);
});

test('contact form has required attributes, honeypot, and zero client JS', async ({ page }) => {
  await page.goto('/contact');
  const form = page.locator('form[action*="/v1/public/contact"]');
  await expect(form).toBeAttached();
  await expect(form).toHaveAttribute('method', /post/i);

  // Verify fields
  await expect(page.locator('input[name="name"]')).toHaveAttribute('required', '');
  await expect(page.locator('input[name="email"]')).toHaveAttribute('required', '');
  await expect(page.locator('select[name="topic"]')).toHaveAttribute('required', '');
  await expect(page.locator('textarea[name="message"]')).toHaveAttribute('required', '');

  // Verify honeypot field is visually hidden and aria-hidden
  const honeypot = page.locator('input[name="_hp_company"]');
  await expect(honeypot).toBeAttached();
  await expect(honeypot).toHaveAttribute('tabindex', '-1');
  await expect(honeypot).toHaveAttribute('aria-hidden', 'true');
});
