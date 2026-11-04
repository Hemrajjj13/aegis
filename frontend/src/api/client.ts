import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from './schema';
import { getAccessToken, refreshAccessToken, notifyUnauthorized } from './authTokens';

/**
 * Fixed development tenant UUID.
 * DEV-ONLY: Defaults to 00000000-0000-0000-0000-000000000001.
 */
export const DEV_TENANT_ID: string =
  (import.meta.env.VITE_DEV_TENANT_ID as string) ||
  '00000000-0000-0000-0000-000000000001';

/**
 * Type-safe HTTP API client generated from ../api/openapi.yaml.
 * Uses Vite proxy (/v1 -> http://localhost:8080/v1).
 */
export const apiClient = createClient<paths>({
  baseUrl: '/v1',
  credentials: 'include',
});

/**
 * Authentication and single-retry middleware for openapi-fetch.
 * 1. Attaches Bearer JWT access token from memory.
 * 2. On HTTP 401, triggers silent refresh and retries the request exactly once.
 * 3. On refresh failure, triggers unauthorized session expiration callback.
 */
export const authMiddleware: Middleware = {
  async onRequest({ request }) {
    const token = getAccessToken();
    if (token && !request.headers.has('Authorization')) {
      request.headers.set('Authorization', `Bearer ${token}`);
    }
    return request;
  },

  async onResponse({ request, response }) {
    if (response.status === 401 && !request.headers.has('X-Auth-Retried')) {
      // Avoid retrying auth endpoints themselves (login, refresh)
      const url = new URL(request.url, typeof window !== 'undefined' ? window.location.origin : 'http://localhost');
      if (!url.pathname.includes('/auth/login') && !url.pathname.includes('/auth/refresh')) {
        const newToken = await refreshAccessToken();
        if (newToken) {
          const retriedHeaders = new Headers(request.headers);
          retriedHeaders.set('Authorization', `Bearer ${newToken}`);
          retriedHeaders.set('X-Auth-Retried', '1');

          // Clone request options for safe single-retry
          const retryRequest = new Request(request, {
            headers: retriedHeaders,
          });
          return fetch(retryRequest);
        } else {
          notifyUnauthorized();
        }
      }
    }
    return response;
  },
};

apiClient.use(authMiddleware);
