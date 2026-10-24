import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('Live Decisions Feed E2E Tests', () => {
  test('submits a transaction via dev form and sees its row within 2 seconds', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });

    // Wait for the Live Decisions header and connection indicator
    await expect(page.getByRole('heading', { name: 'Live Decisions' })).toBeVisible();
    await expect(page.getByRole('status', { name: /Stream Status/ })).toBeVisible({ timeout: 10000 });

    // Open dev test transaction modal
    const sendButton = page.getByRole('button', { name: 'Send Test Transaction' });
    await expect(sendButton).toBeVisible();
    await sendButton.click();

    // Verify modal dialog opens
    const modal = page.getByRole('dialog', { name: 'Send Test Transaction' });
    await expect(modal).toBeVisible();

    // Capture the generated Event ID from the form
    const eventIdInput = modal.locator('#input-event-id');
    await expect(eventIdInput).toBeVisible();
    const eventId = await eventIdInput.inputValue();
    expect(eventId).toBeTruthy();

    // Submit transaction
    const postButton = modal.getByRole('button', { name: 'POST Transaction' });
    const startTime = Date.now();
    await postButton.click();

    // Invariant: row must appear in the table within 2 seconds
    const rowLocator = page.locator(`[role="row"]:has-text("${eventId}")`);
    await expect(rowLocator).toBeVisible({ timeout: 2000 });

    const elapsed = Date.now() - startTime;
    console.log(`[E2E] Transaction ${eventId} appeared in table in ${elapsed}ms (spec limit: 2000ms).`);

    // Verify decision outcome badge is rendered in the row
    await expect(rowLocator.getByRole('status', { name: /Outcome:/ })).toBeVisible();

    // Close modal
    await modal.getByRole('button', { name: 'Close', exact: true }).click();
    await expect(modal).not.toBeVisible();
  });

  test('zero serious or critical accessibility violations in dark theme', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Live Decisions' })).toBeVisible();

    // Ensure dark theme is active
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    const seriousOrCritical = accessibilityScanResults.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical'
    );

    if (seriousOrCritical.length > 0) {
      console.error('A11y Violations (Dark Theme):', JSON.stringify(seriousOrCritical, null, 2));
    }

    expect(seriousOrCritical.length).toBe(0);
  });

  test('zero serious or critical accessibility violations in light theme', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Live Decisions' })).toBeVisible();

    // Toggle to light theme
    const themeButton = page.getByRole('button', { name: /Switch to light theme/ });
    await themeButton.click();

    // Ensure light theme is active
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    const seriousOrCritical = accessibilityScanResults.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical'
    );

    if (seriousOrCritical.length > 0) {
      console.error('A11y Violations (Light Theme):', JSON.stringify(seriousOrCritical, null, 2));
    }

    expect(seriousOrCritical.length).toBe(0);
  });
});
