import { test, expect, type APIRequestContext } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { generateSync } from 'otplib';

interface MailboxItem {
  destination: string;
  purpose?: string;
  code?: string;
  link?: string;
}

function computeTotp(secret: string): string {
  return generateSync({ secret });
}

async function getLatestOtp(request: APIRequestContext, destination: string, purpose: string): Promise<string> {
  for (let i = 0; i < 20; i++) {
    const res = await request.get('/dev/mailbox');
    if (res.ok()) {
      const items = (await res.json()) as MailboxItem[];
      const match = items.find((it) => it.destination === destination && it.purpose === purpose);
      if (match && match.code) {
        return match.code;
      }
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`Timeout waiting for mailbox OTP: destination=${destination}, purpose=${purpose}`);
}

async function getLatestInviteToken(request: APIRequestContext, email: string): Promise<string> {
  for (let i = 0; i < 20; i++) {
    const res = await request.get('/dev/mailbox');
    if (res.ok()) {
      const items = (await res.json()) as MailboxItem[];
      const match = items.find((it) => it.destination === email && (it.purpose?.includes('INVITE') || it.link));
      if (match && match.link) {
        const url = new URL(match.link);
        const token = url.searchParams.get('token');
        if (token) return token;
      }
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`Timeout waiting for invite token: destination=${email}`);
}

test.describe('Console Authentication UI Lifecycle E2E', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeAll(async ({ request }) => {
    await request.post('http://127.0.0.1:8080/dev/mailbox/reset');
  });

  const runId = Math.random().toString(36).substring(2, 7);
  const ownerEmail = `owner_${runId}@example.com`;
  const ownerMobile = `+9198${Math.floor(10000000 + Math.random() * 90000000)}`;
  const ownerPassword = 'ValidPassword123!@#';
  let ownerTotpSecret = '';

  const devEmail = `dev_${runId}@example.com`;
  const devMobile = `+9197${Math.floor(10000000 + Math.random() * 90000000)}`;
  const devPassword = 'DevPassword123!@#';
  let devTotpSecret = '';

  test('1. Protected-route redirect: unauthenticated access to /decisions redirects to /login', async ({ page }) => {
    await page.goto('/decisions');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: /Sign In to Aegis Console/i })).toBeVisible();
  });

  test('2. Owner registration: signup -> mobile OTP -> local QR TOTP setup -> account ready', async ({ page, request }) => {
    await page.goto('/signup');
    await expect(page.getByRole('heading', { name: /Register Organization/i })).toBeVisible();

    // Fill Step 1
    await page.locator('#signup-org-name').fill(`Acme Financial ${runId}`);
    await page.locator('#signup-owner-name').fill(`Owner ${runId}`);
    await page.locator('#signup-email').fill(ownerEmail);
    await page.locator('#signup-mobile').fill(ownerMobile);
    await page.locator('#signup-password').fill(ownerPassword);
    await page.locator('#signup-confirm-password').fill(ownerPassword);

    await page.getByRole('button', { name: /Next: Mobile Verification/i }).click();

    // Step 2: Mobile OTP
    await expect(page.getByRole('heading', { name: /Verify Mobile Number/i })).toBeVisible();
    const otp = await getLatestOtp(request, ownerMobile, 'SIGNUP_MOBILE_VERIFY');
    await page.locator('#signup-otp').fill(otp);
    await page.getByRole('button', { name: /Verify Mobile & Setup 2FA/i }).click();

    // Step 3: TOTP Setup
    await expect(page.getByRole('heading', { name: /Configure Two-Factor Authentication/i })).toBeVisible();
    // Verify QR code is rendered locally (img tag present)
    const qrImg = page.getByRole('img', { name: /TOTP Authenticator QR Code/i });
    await expect(qrImg).toBeVisible();

    // Read manual secret key
    const secretInput = page.locator('#manual-totp-key');
    await expect(secretInput).not.toHaveValue('');
    ownerTotpSecret = await secretInput.inputValue();
    expect(ownerTotpSecret).toBeTruthy();

    // Compute code via otplib
    const totpCode = computeTotp(ownerTotpSecret);
    await page.locator('#signup-totp-verify').fill(totpCode);
    await page.getByRole('button', { name: /Complete Registration/i }).click();

    // Step 4: Account Ready
    await expect(page.getByRole('heading', { name: /Account Ready/i })).toBeVisible();
    await page.getByRole('button', { name: /Sign In with Master Password/i }).click();
    await expect(page).toHaveURL(/\/login/);
  });

  test('3. Login flow: credentials -> 2FA TOTP challenge -> role-based dashboard landing', async ({ page }) => {
    await page.goto('/login');
    await page.locator('#login-identifier').fill(ownerEmail);
    await page.locator('#login-password').fill(ownerPassword);
    await page.getByRole('button', { name: /Continue/i }).click();

    // MFA Challenge step
    await expect(page.getByRole('heading', { name: /Two-Factor Authentication/i })).toBeVisible();
    const totpCode = computeTotp(ownerTotpSecret);
    await page.locator('#login-totp').fill(totpCode);
    await page.getByRole('button', { name: /Verify and Access/i }).click();

    // Owner role lands on /settings placeholder page
    await expect(page).toHaveURL(/\/settings/);
    await expect(page.getByRole('heading', { name: /Tenant Administration/i })).toBeVisible();
  });

  test('4. Reload retains authenticated session via silent refresh', async ({ page }) => {
    // Log in first
    await page.goto('/login');
    await page.locator('#login-identifier').fill(ownerEmail);
    await page.locator('#login-password').fill(ownerPassword);
    await page.getByRole('button', { name: /Continue/i }).click();
    const totpCode = computeTotp(ownerTotpSecret);
    await page.locator('#login-totp').fill(totpCode);
    await page.getByRole('button', { name: /Verify and Access/i }).click();
    await expect(page).toHaveURL(/\/settings/);

    // Reload browser page
    await page.reload();

    // Should remain on /settings and not redirect to /login
    await expect(page).toHaveURL(/\/settings/);
    await expect(page.getByRole('heading', { name: /Tenant Administration/i })).toBeVisible();
  });

  test('5. Logout revokes session and redirects to /login', async ({ page }) => {
    // Log in
    await page.goto('/login');
    await page.locator('#login-identifier').fill(ownerEmail);
    await page.locator('#login-password').fill(ownerPassword);
    await page.getByRole('button', { name: /Continue/i }).click();
    const totpCode = computeTotp(ownerTotpSecret);
    await page.locator('#login-totp').fill(totpCode);
    await page.getByRole('button', { name: /Verify and Access/i }).click();
    await expect(page).toHaveURL(/\/settings/);

    // Click Sign Out
    await page.getByRole('banner').getByRole('button', { name: /Sign Out/i }).click();
    await expect(page).toHaveURL(/\/login/);

    // Attempting to revisit /settings redirects to /login
    await page.goto('/settings');
    await expect(page).toHaveURL(/\/login/);
  });

  test('6. Invite acceptance flow: invite token -> details & TOTP setup -> developer landing on /decisions', async ({ page, request }) => {
    // 1. Authenticate owner via API to obtain access token and create an invite
    const loginRes = await request.post('/v1/auth/login', {
      data: { identifier: ownerEmail, password: ownerPassword },
    });
    const loginData = await loginRes.json();
    const challengeRes = await request.post('/v1/auth/totp/challenge', {
      data: { mfa_token: loginData.mfa_token, totp_code: computeTotp(ownerTotpSecret) },
    });
    const challengeData = await challengeRes.json();
    const ownerToken = challengeData.access_token;

    // Create an invite for a developer via POST /v1/tenant/invites
    const inviteRes = await request.post('/v1/tenant/invites', {
      headers: {
        'Authorization': `Bearer ${ownerToken}`,
        'Content-Type': 'application/json',
      },
      data: {
        email: devEmail,
        role: 'developer',
      },
    });
    expect(inviteRes.ok()).toBeTruthy();

    // Retrieve invite token from mailbox
    const token = await getLatestInviteToken(request, devEmail);
    expect(token).toBeTruthy();

    // 2. Developer visits invite acceptance URL
    await page.goto(`/invite/accept?token=${token}`);
    await expect(page.getByRole('heading', { name: /Join/i })).toBeVisible();
    await expect(page.locator('#invite-email')).toHaveValue(devEmail);

    // Fill registration info
    await page.locator('#invite-name').fill(`Dev User ${runId}`);
    await page.locator('#invite-mobile').fill(devMobile);
    await page.locator('#invite-password').fill(devPassword);
    await page.locator('#invite-confirm-password').fill(devPassword);

    // Read TOTP secret from page and compute code
    devTotpSecret = await page.locator('#manual-totp-key').inputValue();
    expect(devTotpSecret).toBeTruthy();
    const devCode = computeTotp(devTotpSecret);
    await page.locator('#invite-totp').fill(devCode);

    await page.getByRole('button', { name: /Accept Invitation & Activate/i }).click();

    // Developer lands on /decisions
    await expect(page).toHaveURL(/\/decisions/);
    await expect(page.getByRole('heading', { name: 'Live Decisions' })).toBeVisible();
  });

  test('7. Forgot password flow: OTP reset -> sign in with new password', async ({ page, request }) => {
    await page.goto('/forgot-password');
    await expect(page.getByRole('heading', { name: /Reset Password/i })).toBeVisible();

    await page.locator('#forgot-identifier').fill(devEmail);
    await page.getByRole('button', { name: /Send Verification Code/i }).click();

    // Generic confirmation displayed
    await expect(page.getByRole('heading', { name: /Enter Verification Code & New Password/i })).toBeVisible();

    // Retrieve reset OTP from mailbox
    const resetOtp = await getLatestOtp(request, devEmail, 'PASSWORD_RESET');
    const updatedPassword = 'NewDevPassword123!@#';

    await page.locator('#reset-otp').fill(resetOtp);
    await page.locator('#reset-new-password').fill(updatedPassword);
    await page.locator('#reset-confirm-password').fill(updatedPassword);
    await page.getByRole('button', { name: /Confirm & Reset Password/i }).click();

    // Complete
    await expect(page.getByRole('heading', { name: /Password Reset Complete/i })).toBeVisible();
    await page.getByRole('button', { name: /Return to Sign In/i }).click();

    // Sign in with new password
    await expect(page).toHaveURL(/\/login/);
    await page.locator('#login-identifier').fill(devEmail);
    await page.locator('#login-password').fill(updatedPassword);
    await page.getByRole('button', { name: /Continue/i }).click();

    const devTotp = computeTotp(devTotpSecret);
    await page.locator('#login-totp').fill(devTotp);
    await page.getByRole('button', { name: /Verify and Access/i }).click();

    await expect(page).toHaveURL(/\/decisions/);
  });

  test('8. Lockout shows generic message on consecutive failures', async ({ page }) => {
    await page.goto('/login');

    for (let i = 0; i < 5; i++) {
      await page.locator('#login-identifier').fill(devEmail);
      await page.locator('#login-password').fill('WrongPassword999!');
      await page.getByRole('button', { name: /Continue/i }).click();
      await expect(page.getByRole('alert')).toBeVisible();
    }

    // 5th attempt triggers lockout, UI displays generic message
    const alertText = await page.getByRole('alert').innerText();
    expect(alertText.length).toBeGreaterThan(0);
    // Ensure no internal stack trace or raw exception leaked
    expect(alertText).not.toContain('Exception');
    expect(alertText).not.toContain('trace');
  });

  test('9. Accessibility (WCAG 2.1 AA) zero serious or critical violations across themes', async ({ page }) => {
    const pagesToAudit = ['/login', '/signup', '/forgot-password', '/dev/mailbox'];

    for (const route of pagesToAudit) {
      await page.goto(route);

      // Audit in Dark theme (default)
      const darkAxe = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();
      const darkViolations = darkAxe.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
      if (darkViolations.length > 0) {
        console.error(`A11y Violations (${route} - Dark):`, JSON.stringify(darkViolations, null, 2));
      }
      expect(darkViolations.length).toBe(0);

      // Toggle to Light theme
      const themeToggle = page.getByRole('button', { name: /Switch to light theme/i });
      if (await themeToggle.isVisible()) {
        await themeToggle.click();
        await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');

        const lightAxe = await new AxeBuilder({ page })
          .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
          .analyze();
        const lightViolations = lightAxe.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
        if (lightViolations.length > 0) {
          console.error(`A11y Violations (${route} - Light):`, JSON.stringify(lightViolations, null, 2));
        }
        expect(lightViolations.length).toBe(0);
      }
    }
  });
});

