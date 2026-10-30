import { describe, it, expect, vi, beforeEach } from 'vitest';
import { getAccessToken, setAccessToken, setOnUnauthorized, notifyUnauthorized } from '../api/authTokens';
import { authMiddleware } from '../api/client';
import { getDefaultLandingRoute } from '../utils/authRoutes';
import { generateBase32Secret, buildOtpAuthUri } from '../utils/totpHelper';

describe('Auth Tokens & In-Memory Storage', () => {
  beforeEach(() => {
    setAccessToken(null);
    localStorage.clear();
    sessionStorage.clear();
  });

  it('stores access token in memory only and never writes to localStorage or sessionStorage', () => {
    const dummyToken = 'eyJhbGciOiJSUzI1NiJ9.dummy.token';
    setAccessToken(dummyToken);

    expect(getAccessToken()).toBe(dummyToken);
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('access_token')).toBeNull();
    expect(sessionStorage.getItem('token')).toBeNull();
    expect(sessionStorage.getItem('access_token')).toBeNull();

    setAccessToken(null);
    expect(getAccessToken()).toBeNull();
  });

  it('triggers onUnauthorized callback when unauthorized is notified', () => {
    const onUnauthorized = vi.fn();
    setOnUnauthorized(onUnauthorized);

    notifyUnauthorized();
    expect(onUnauthorized).toHaveBeenCalledTimes(1);

    setOnUnauthorized(null);
  });
});

describe('Default Landing Routes Matrix', () => {
  it('maps each canonical role to its designated operational dashboard', () => {
    expect(getDefaultLandingRoute('owner')).toBe('/settings');
    expect(getDefaultLandingRoute('admin')).toBe('/settings/members');
    expect(getDefaultLandingRoute('risk_manager')).toBe('/rules');
    expect(getDefaultLandingRoute('rule_author')).toBe('/rules');
    expect(getDefaultLandingRoute('risk_analyst')).toBe('/cases');
    expect(getDefaultLandingRoute('auditor')).toBe('/audit');
    expect(getDefaultLandingRoute('developer')).toBe('/decisions');
    expect(getDefaultLandingRoute(undefined)).toBe('/decisions');
  });
});

describe('TOTP Base32 & URI Helper', () => {
  it('generates valid RFC 3548 Base32 secret without invalid characters', () => {
    const secret = generateBase32Secret(20);
    expect(secret.length).toBe(32);
    expect(/^[A-Z2-7]+$/.test(secret)).toBe(true);
  });

  it('constructs well-formed otpauth URI for authenticator applications', () => {
    const secret = 'JBSWY3DPEHPK3PXP';
    const uri = buildOtpAuthUri(secret, 'analyst@bank.com', 'Aegis');
    expect(uri).toContain('otpauth://totp/Aegis%3Aanalyst%40bank.com');
    expect(uri).toContain('secret=JBSWY3DPEHPK3PXP');
    expect(uri).toContain('issuer=Aegis');
    expect(uri).toContain('digits=6');
  });
});

describe('OpenAPI Auth Middleware', () => {
  beforeEach(() => {
    setAccessToken(null);
    vi.restoreAllMocks();
  });

  it('attaches Bearer token in onRequest when access token is present', async () => {
    setAccessToken('valid-jwt-token-xyz');
    const req = new Request('http://localhost/v1/decisions', {
      headers: { 'Accept': 'application/json' },
    });

    const modifiedReq = await authMiddleware.onRequest!({
      request: req,
      schemaPath: '/decisions',
      params: {},
      id: 'req-1',
      options: {} as unknown as Parameters<NonNullable<typeof authMiddleware.onRequest>>[0]['options'],
    });

    const finalReq = (modifiedReq as Request) || req;
    expect(finalReq.headers.get('Authorization')).toBe('Bearer valid-jwt-token-xyz');
  });

  it('does not overwrite existing Authorization header in onRequest', async () => {
    setAccessToken('valid-jwt-token-xyz');
    const req = new Request('http://localhost/v1/decisions', {
      headers: { 'Authorization': 'Bearer custom-token' },
    });

    const modifiedReq = await authMiddleware.onRequest!({
      request: req,
      schemaPath: '/decisions',
      params: {},
      id: 'req-2',
      options: {} as unknown as Parameters<NonNullable<typeof authMiddleware.onRequest>>[0]['options'],
    });

    const finalReq = (modifiedReq as Request) || req;
    expect(finalReq.headers.get('Authorization')).toBe('Bearer custom-token');
  });

  it('retries request once on 401 when silent refresh succeeds', async () => {
    const originalReq = new Request('http://localhost/v1/decisions');
    const unauthorizedResp = new Response(JSON.stringify({ error: 'Unauthorized' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' },
    });

    // Mock global fetch: first for /auth/refresh, then for retried /v1/decisions
    const globalFetch = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
      if (url.includes('/auth/refresh')) {
        return new Response(JSON.stringify({
          access_token: 'new-refreshed-token-123',
          token_type: 'Bearer',
          expires_in: 900,
        }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ success: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    const retriedResponse = await authMiddleware.onResponse!({
      request: originalReq,
      response: unauthorizedResp,
      schemaPath: '/decisions',
      params: {},
      id: 'req-3',
      options: {} as unknown as Parameters<NonNullable<typeof authMiddleware.onResponse>>[0]['options'],
    });

    expect(retriedResponse).toBeDefined();
    expect(retriedResponse!.status).toBe(200);
    expect(getAccessToken()).toBe('new-refreshed-token-123');
    globalFetch.mockRestore();
  });
});
