import createClient from 'openapi-fetch';
import type { paths } from './schema';

/**
 * Fixed development tenant UUID.
 * DEV-ONLY: Defaults to 00000000-0000-0000-0000-000000000001.
 * Will be removed when auth and tenant session context lands in Milestone M8.
 */
export const DEV_TENANT_ID: string =
  (import.meta.env.VITE_DEV_TENANT_ID as string) ||
  '00000000-0000-0000-0000-000000000001';

/**
 * Type-safe HTTP API client generated from ../api/openapi.yaml.
 * Uses Vite proxy (/v1 -> http://localhost:8080/v1) and attaches required dev headers.
 * 
 * DEV-ONLY: Injects X-Tenant-Id header on all requests.
 * MUST be removed in Milestone M8 once session authentication is established.
 */
export const apiClient = createClient<paths>({
  baseUrl: '/v1',
  headers: {
    'X-Tenant-Id': DEV_TENANT_ID,
  },
});
