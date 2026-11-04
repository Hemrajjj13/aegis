import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { AuthLayout } from '../components/auth/AuthLayout';
import { PasswordInput } from '../components/auth/PasswordInput';
import { useAuth, getDefaultLandingRoute } from '../contexts/AuthContext';
import { ShieldCheck, KeyRound, AlertCircle } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, loginChallenge, sessionExpired, clearSessionExpired } = useAuth();

  // Form states
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [mfaToken, setMfaToken] = useState<string | null>(null);
  const [totpCode, setTotpCode] = useState('');
  const [isRecoveryMode, setIsRecoveryMode] = useState(false);
  const [recoveryCode, setRecoveryCode] = useState('');

  // UI status
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // References for focus management
  const identifierInputRef = useRef<HTMLInputElement>(null);
  const totpInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!mfaToken && identifierInputRef.current) {
      identifierInputRef.current.focus();
    } else if (mfaToken && totpInputRef.current) {
      totpInputRef.current.focus();
    }
  }, [mfaToken]);

  // Handle Step 1: Identifier & Password
  const handleSubmitCredentials = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;

    setErrorMessage(null);
    clearSessionExpired();

    if (!identifier.trim() || !password) {
      setErrorMessage('Please enter both your identifier and password.');
      return;
    }

    setIsSubmitting(true);
    try {
      const res = await login(identifier.trim(), password);
      if (res.mfaRequired && res.mfaToken) {
        setMfaToken(res.mfaToken);
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Invalid credentials or account locked.';
      setErrorMessage(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle Step 2: TOTP / Recovery Code Challenge
  const handleSubmitMfa = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting || !mfaToken) return;

    setErrorMessage(null);
    const code = isRecoveryMode ? recoveryCode.trim() : totpCode.trim();

    if (!code) {
      setErrorMessage(isRecoveryMode ? 'Please enter a recovery code.' : 'Please enter your 6-digit TOTP code.');
      return;
    }

    setIsSubmitting(true);
    try {
      const profile = await loginChallenge(mfaToken, code, isRecoveryMode);
      // Navigate to intended page or default role dashboard
      const routerState = location.state as { from?: { pathname?: string } } | null;
      const stateFrom = routerState?.from?.pathname;
      const destination = stateFrom || getDefaultLandingRoute(profile.role);
      navigate(destination, { replace: true });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Invalid verification code.';
      setErrorMessage(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const isExpiredBanner = sessionExpired || new URLSearchParams(location.search).get('expired') === '1';

  return (
    <AuthLayout
      title={mfaToken ? 'Two-Factor Authentication' : 'Sign In to Aegis Console'}
      subtitle={
        mfaToken
          ? isRecoveryMode
            ? 'Enter one of your 10-character backup recovery codes.'
            : 'Enter the 6-digit verification code from your authenticator application.'
          : 'Enter your credentials to access the real-time decision dashboard.'
      }
      banner={
        isExpiredBanner ? (
          <div
            role="alert"
            className="p-3 bg-review-bg border border-review text-review rounded-md text-xs flex items-center gap-2"
          >
            <AlertCircle className="w-4 h-4 shrink-0" aria-hidden="true" />
            <span>Your session has expired. Please sign in again to continue.</span>
          </div>
        ) : null
      }
    >
      {/* Global Error Banner */}
      {errorMessage && (
        <div
          role="alert"
          aria-live="assertive"
          className="p-3 bg-decline-bg border border-decline text-decline-text rounded-md text-xs flex items-center gap-2"
        >
          <AlertCircle className="w-4 h-4 shrink-0" aria-hidden="true" />
          <span>{errorMessage}</span>
        </div>
      )}

      {!mfaToken ? (
        /* STEP 1: CREDENTIALS */
        <form onSubmit={handleSubmitCredentials} className="flex flex-col gap-4" noValidate>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="login-identifier" className="text-xs font-medium text-text">
              Email or Mobile Number
            </label>
            <input
              ref={identifierInputRef}
              id="login-identifier"
              name="identifier"
              type="text"
              autoComplete="username"
              required
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder="analyst@bank.com or +919876543210"
              className="w-full px-3 py-2 text-xs rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
            />
          </div>

          <PasswordInput
            id="login-password"
            name="password"
            label="Password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />

          <div className="flex justify-between items-center text-xs">
            <Link
              to="/forgot-password"
              className="text-accent hover:underline focus:outline-none focus:ring-1 focus:ring-accent rounded px-1"
            >
              Forgot password?
            </Link>
            <Link
              to="/signup"
              className="text-text-muted hover:text-text underline focus:outline-none focus:ring-1 focus:ring-accent rounded px-1"
            >
              Register organization
            </Link>
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            aria-busy={isSubmitting}
            className="w-full mt-2 py-2 px-4 bg-accent-solid hover:bg-accent-hover text-white text-xs font-semibold rounded shadow transition-colors flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-surface"
          >
            {isSubmitting ? (
              <span>Authenticating...</span>
            ) : (
              <>
                <span>Continue</span>
                <KeyRound className="w-3.5 h-3.5" aria-hidden="true" />
              </>
            )}
          </button>
        </form>
      ) : (
        /* STEP 2: TOTP CHALLENGE */
        <form onSubmit={handleSubmitMfa} className="flex flex-col gap-4" noValidate>
          {!isRecoveryMode ? (
            <div className="flex flex-col gap-1.5">
              <label htmlFor="login-totp" className="text-xs font-medium text-text">
                6-Digit Authenticator Code
              </label>
              <input
                ref={totpInputRef}
                id="login-totp"
                name="totp"
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={6}
                autoComplete="one-time-code"
                required
                value={totpCode}
                onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, ''))}
                placeholder="123456"
                className="w-full font-mono text-center tracking-widest text-lg px-3 py-2 rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
              />
            </div>
          ) : (
            <div className="flex flex-col gap-1.5">
              <label htmlFor="login-recovery" className="text-xs font-medium text-text">
                Emergency Backup Recovery Code
              </label>
              <input
                ref={totpInputRef}
                id="login-recovery"
                name="recovery"
                type="text"
                autoComplete="off"
                required
                value={recoveryCode}
                onChange={(e) => setRecoveryCode(e.target.value.toUpperCase())}
                placeholder="ABCD1234EF"
                className="w-full font-mono text-center tracking-wider text-sm px-3 py-2 rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
              />
            </div>
          )}

          <div className="flex justify-between items-center text-xs">
            <button
              type="button"
              onClick={() => {
                setIsRecoveryMode((prev) => !prev);
                setErrorMessage(null);
              }}
              className="text-accent hover:underline focus:outline-none focus:ring-1 focus:ring-accent rounded px-1"
            >
              {isRecoveryMode ? 'Use authenticator app' : 'Use recovery code'}
            </button>
            <button
              type="button"
              onClick={() => {
                setMfaToken(null);
                setTotpCode('');
                setRecoveryCode('');
                setErrorMessage(null);
              }}
              className="text-text-muted hover:text-text underline focus:outline-none focus:ring-1 focus:ring-accent rounded px-1"
            >
              Back to login
            </button>
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            aria-busy={isSubmitting}
            className="w-full mt-2 py-2 px-4 bg-accent-solid hover:bg-accent-hover text-white text-xs font-semibold rounded shadow transition-colors flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-surface"
          >
            {isSubmitting ? (
              <span>Verifying...</span>
            ) : (
              <>
                <ShieldCheck className="w-4 h-4" aria-hidden="true" />
                <span>Verify and Access</span>
              </>
            )}
          </button>
        </form>
      )}
    </AuthLayout>
  );
};
