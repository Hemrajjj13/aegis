/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { setAccessToken, refreshAccessToken, setOnUnauthorized } from '../api/authTokens';
import type { UserProfile, AuthContextType } from '../types/auth';

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

function extractClaims(token: string): Partial<UserProfile> {
  try {
    const base64Url = token.split('.')[1];
    if (!base64Url) return {};
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    const parsed = JSON.parse(jsonPayload);
    return {
      id: parsed.sub,
      user_id: parsed.sub,
      tenant_id: parsed.tenant_id,
      role: parsed.role,
      email: parsed.email,
      full_name: parsed.full_name,
    };
  } catch {
    return {};
  }
}

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [accessTokenState, setAccessTokenState] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [sessionExpired, setSessionExpired] = useState<boolean>(false);

  const clearSessionExpired = useCallback(() => {
    setSessionExpired(false);
  }, []);

  const setSessionUser = useCallback((userProfile: UserProfile, token: string) => {
    setAccessToken(token);
    setAccessTokenState(token);
    const claims = extractClaims(token);
    setUser({
      ...claims,
      ...userProfile,
      tenant_id: userProfile.tenant_id || claims.tenant_id || '',
      role: userProfile.role || claims.role || 'developer',
    });
    setSessionExpired(false);
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setAccessTokenState(null);
    setUser(null);
  }, []);

  // Logout
  const logout = useCallback(async () => {
    try {
      await fetch('/v1/auth/logout', {
        method: 'POST',
        credentials: 'include',
      });
    } catch {
      // Ignore network errors on logout
    } finally {
      clearSession();
    }
  }, [clearSession]);

  // Handle unauthorized callback from api middleware
  useEffect(() => {
    setOnUnauthorized(() => {
      clearSession();
      setSessionExpired(true);
    });
    return () => setOnUnauthorized(null);
  }, [clearSession]);

  // Silent refresh
  const refresh = useCallback(async (): Promise<boolean> => {
    const newToken = await refreshAccessToken();
    if (!newToken) {
      clearSession();
      return false;
    }

    setAccessTokenState(newToken);
    try {
      // Fetch /auth/me to retrieve updated user profile
      const res = await fetch('/v1/auth/me', {
        headers: {
          'Authorization': `Bearer ${newToken}`,
        },
      });
      if (res.ok) {
        const profile = (await res.json()) as UserProfile;
        const claims = extractClaims(newToken);
        setUser({
          ...claims,
          ...profile,
          tenant_id: profile.tenant_id || claims.tenant_id || '',
          role: profile.role || claims.role || 'developer',
        });
        return true;
      }
    } catch {
      // If fetching me failed, token might be invalid
    }
    clearSession();
    return false;
  }, [clearSession]);

  // Silent refresh on mount
  useEffect(() => {
    let isMounted = true;
    (async () => {
      setIsLoading(true);
      await refresh();
      if (isMounted) {
        setIsLoading(false);
      }
    })();
    return () => {
      isMounted = false;
    };
  }, [refresh]);

  // Login step 1
  const login = useCallback(async (identifier: string, password: string) => {
    const res = await fetch('/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ identifier, password }),
      credentials: 'include',
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      const msg = err.detail || err.error || err.title || 'Invalid credentials or account locked.';
      throw new Error(msg);
    }

    const data = await res.json();
    return {
      mfaRequired: !!data.mfa_required,
      mfaToken: data.mfa_token as string | undefined,
    };
  }, []);

  // Login step 2 (MFA Challenge)
  const loginChallenge = useCallback(async (mfaToken: string, code: string, isRecoveryCode = false) => {
    const payload: Record<string, string> = { mfa_token: mfaToken };
    if (isRecoveryCode) {
      payload.recovery_code = code;
    } else {
      payload.totp_code = code;
    }

    const res = await fetch('/v1/auth/totp/challenge', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      credentials: 'include',
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.error || err.detail || 'Invalid verification code.');
    }

    const data = await res.json();
    const token = data.access_token as string;
    const userProfile = data.user as UserProfile;

    setSessionUser(userProfile, token);
    return userProfile;
  }, [setSessionUser]);

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken: accessTokenState,
        isAuthenticated: !!user && !!accessTokenState,
        isLoading,
        sessionExpired,
        clearSessionExpired,
        login,
        loginChallenge,
        logout,
        refresh,
        setSessionUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

export { getDefaultLandingRoute } from '../utils/authRoutes';
export type { UserProfile, UserRole, AuthContextType } from '../types/auth';
