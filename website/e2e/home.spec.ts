import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { mkdirSync } from 'node:fs';

const anchors = ['solutions', 'features', 'how-it-works', 'resources', 'faq', 'about', 'careers'];

test.beforeAll(() => {
  try {
    mkdirSync('.screens', { recursive: true });
  } catch { }
});

test('nav anchor targets exist', async ({ page }) => {
  await page.goto('/');
  for (const a of anchors) {
    await expect(page.locator('#' + a)).toHaveCount(1);
  }
});

test('skip link focuses main', async ({ page }) => {
  await page.goto('/');
  await page.keyboard.press('Tab');
  const skip = page.getByText('Skip to content');
  await expect(skip).toBeFocused();
});

test('mobile menu at 375px opens and closes on Escape', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  await page.goto('/');
  const btn = page.getByRole('button', { name: 'Open menu' });
  await btn.click();
  const menu = page.getByRole('dialog', { name: 'Mobile menu' });
  await expect(menu).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(menu).toBeHidden();
});

test('mobile menu closes on link click', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  await page.goto('/');
  const btn = page.getByRole('button', { name: 'Open menu' });
  await btn.click();
  const menu = page.getByRole('dialog', { name: 'Mobile menu' });
  await expect(menu).toBeVisible();
  await menu.getByText('Features').click();
  await expect(menu).toBeHidden();
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
    test(`axe ${theme} ${width}`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.goto('/');
      await page.evaluate((t) => {
        document.documentElement.setAttribute('data-theme', t);
        document.documentElement.classList.toggle('light', t === 'light');
        try { localStorage.setItem('theme', t); } catch (e) { }
      }, theme);
      await page.reload();
      const results = await new AxeBuilder({ page }).analyze();
      const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
      expect(serious).toEqual([]);
      await page.screenshot({ path: `.screens/home-${theme}-${width}.png`, fullPage: true });
    });
  }
}

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
