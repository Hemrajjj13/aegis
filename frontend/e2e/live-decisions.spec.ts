import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { generateSync } from 'otplib';

interface MailboxItem {
  destination: string;
  purpose?: string;
  code?: string;
}

async function ensureAuthenticatedDeveloper(page: Page, request: APIRequestContext) {
  await request.post('http://127.0.0.1:8080/dev/mailbox/reset');
  const runId = Math.random().toString(36).substring(2, 7);
  const email = `live_${runId}@example.com`;
  const mobile = `+9196${Math.floor(10000000 + Math.random() * 90000000)}`;
  const password = 'LivePassword123!@#';

  await page.goto('/signup');
  await page.locator('#signup-org-name').fill(`Live Test Org ${runId}`);
  await page.locator('#signup-owner-name').fill(`Live Tester ${runId}`);
  await page.locator('#signup-email').fill(email);
  await page.locator('#signup-mobile').fill(mobile);
  await page.locator('#signup-password').fill(password);
  await page.locator('#signup-confirm-password').fill(password);
  await page.getByRole('button', { name: /Next: Mobile Verification/i }).click();

  // Retrieve OTP
  let otpFound = false;
  for (let i = 0; i < 20; i++) {
    const res = await request.get('/dev/mailbox');
    if (res.ok()) {
      const items = (await res.json()) as MailboxItem[];
      const match = items.find((it) => it.destination === mobile && it.purpose === 'SIGNUP_MOBILE_VERIFY');
      if (match && match.code) {
        await page.locator('#signup-otp').fill(match.code);
        otpFound = true;
        break;
      }
    }
    await page.waitForTimeout(500);
  }
  expect(otpFound).toBeTruthy();
  await page.getByRole('button', { name: /Verify Mobile & Setup 2FA/i }).click();

  // Retrieve TOTP secret & verify
  const secretInput = page.locator('#manual-totp-key');
  await expect(secretInput).not.toHaveValue('');
  const secret = await secretInput.inputValue();
  const totpCode = generateSync({ secret });
  await page.locator('#signup-totp-verify').fill(totpCode);
  await page.getByRole('button', { name: /Complete Registration/i }).click();

  // Login
  await page.getByRole('button', { name: /Sign In with Master Password/i }).click();
  await page.locator('#login-identifier').fill(email);
  await page.locator('#login-password').fill(password);
  await page.getByRole('button', { name: /Continue/i }).click();
  await expect(page.locator('#login-totp')).toBeVisible();
  const loginCode = generateSync({ secret });
  await page.locator('#login-totp').fill(loginCode);
  await page.getByRole('button', { name: /Verify and Access/i }).click();

  // Navigate to Live Decisions
  await page.goto('/decisions');
  await expect(page.getByRole('heading', { name: 'Live Decisions' })).toBeVisible();
}

test.describe('Live Decisions Feed E2E Tests', () => {
  test.beforeEach(async ({ page, request }) => {
    await ensureAuthenticatedDeveloper(page, request);
  });

  test('submits a transaction via dev form and sees its row within 2 seconds', async ({ page }) => {
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
