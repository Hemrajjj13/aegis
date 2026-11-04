/**
 * In-memory token management for Project Aegis Console.
 * 
 * Strict Security Invariant (Document 19 & ADR-0011):
 * Access tokens are stored IN-MEMORY ONLY.
 * Never write access tokens or refresh tokens to localStorage, sessionStorage, or IndexedDB.
 */

let memoryAccessToken: string | null = null;
let onUnauthorizedCallback: (() => void) | null = null;
let refreshPromise: Promise<string | null> | null = null;

export function getAccessToken(): string | null {
  return memoryAccessToken;
}

export function setAccessToken(token: string | null): void {
  memoryAccessToken = token;
}

export function setOnUnauthorized(cb: (() => void) | null): void {
  onUnauthorizedCallback = cb;
}

export function notifyUnauthorized(): void {
  if (onUnauthorizedCallback) {
    onUnauthorizedCallback();
  }
}

/**
 * Execute silent refresh against /v1/auth/refresh.
 * Includes X-Aegis-CSRF header and credentials: 'include'.
 * Deduplicates concurrent refresh requests via a single shared Promise.
 */
export async function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    try {
      const response = await fetch('/v1/auth/refresh', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Aegis-CSRF': '1',
        },
        credentials: 'include',
      });

      if (!response.ok) {
        setAccessToken(null);
        return null;
      }

      const data = await response.json();
      const newToken = data.access_token as string;
      setAccessToken(newToken);
      return newToken;
    } catch {
      setAccessToken(null);
      return null;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

