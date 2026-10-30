import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AuthLayout } from '../components/auth/AuthLayout';
import { PasswordInput } from '../components/auth/PasswordInput';
import { KeyRound, ShieldCheck, CheckCircle } from 'lucide-react';

export const ForgotPasswordPage: React.FC = () => {
  const navigate = useNavigate();

  // Multi-step: 1 = Request Reset, 2 = Verify OTP & Reset, 3 = Success
  const [step, setStep] = useState<1 | 2 | 3>(1);

  // Form states
  const [identifier, setIdentifier] = useState('');
  const [otpCode, setOtpCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [totpCode, setTotpCode] = useState('');

  // Status & Error
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);

  const identifierRef = useRef<HTMLInputElement>(null);
  const otpRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (step === 1 && identifierRef.current) {
      identifierRef.current.focus();
    } else if (step === 2 && otpRef.current) {
      otpRef.current.focus();
    }
  }, [step]);

  // Handle Step 1: Request OTP
  const handleRequestOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;
    setErrorMessage(null);

    if (!identifier.trim()) {
      setErrorMessage('Please enter your email or mobile number.');
      return;
    }

    setIsSubmitting(true);
    try {
      const res = await fetch('/v1/auth/password/forgot', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ identifier: identifier.trim() }),
      });

      const data = await res.json().catch(() => ({}));
      setInfoMessage(data.message || 'If an account exists, a verification code has been dispatched.');
      setStep(2);
    } catch {
      // Generic message to prevent enumeration
      setInfoMessage('If an account exists, a verification code has been dispatched.');
      setStep(2);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle Step 2: Submit Reset
  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;
    setErrorMessage(null);

    if (!otpCode.trim() || otpCode.trim().length !== 6) {
      setErrorMessage('Please enter the 6-digit verification code.');
      return;
    }
    if (newPassword.length < 12) {
      setErrorMessage('New password must be at least 12 characters.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setErrorMessage('Passwords do not match.');
      return;
    }

    setIsSubmitting(true);
    try {
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      };
      if (totpCode.trim()) {
        headers['X-Aegis-TOTP'] = totpCode.trim();
      }

      const res = await fetch('/v1/auth/password/reset', {
        method: 'POST',
        headers,
        body: JSON.stringify({
          identifier: identifier.trim(),
          otp_code: otpCode.trim(),
          new_password: newPassword,
        }),
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || err.detail || 'Password reset failed.');
      }

      setStep(3);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Password reset failed.';
      setErrorMessage(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title={
        step === 1
          ? 'Reset Password'
          : step === 2
            ? 'Enter Verification Code & New Password'
            : 'Password Reset Complete'
      }
      subtitle={
        step === 1
          ? 'Enter your registered email or mobile to receive a one-time verification code.'
          : step === 2
            ? 'Enter the 6-digit OTP sent to your registered contact method and define your new password.'
            : 'Your account password has been successfully updated.'
      }
    >
      {errorMessage && (
        <div
          role="alert"
          aria-live="assertive"
          className="p-3 bg-decline-bg border border-decline text-decline-text rounded-md text-xs flex items-center gap-2"
        >
          <span>{errorMessage}</span>
        </div>
      )}

      {infoMessage && step === 2 && (
        <div
          role="status"
          className="p-3 bg-info-bg border border-info text-info rounded-md text-xs flex items-center gap-2"
        >
          <span>{infoMessage}</span>
        </div>
      )}

      {step === 1 && (
        <form onSubmit={handleRequestOtp} className="flex flex-col gap-4" noValidate>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="forgot-identifier" className="text-xs font-medium text-text">
              Email or Mobile Number
            </label>
            <input
              ref={identifierRef}
              id="forgot-identifier"
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

          <div className="flex justify-between items-center text-xs">
            <Link
              to="/login"
              className="text-text-muted hover:text-text underline focus:outline-none focus:ring-1 focus:ring-accent rounded px-1"
            >
              Back to Sign In
            </Link>
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            aria-busy={isSubmitting}
            className="w-full mt-2 py-2 px-4 bg-accent-solid hover:bg-accent-hover text-white text-xs font-semibold rounded shadow transition-colors flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-surface"
          >
            {isSubmitting ? (
              <span>Sending Code...</span>
            ) : (
              <>
                <KeyRound className="w-3.5 h-3.5" aria-hidden="true" />
                <span>Send Verification Code</span>
              </>
            )}
          </button>
        </form>
      )}

      {step === 2 && (
        <form onSubmit={handleResetPassword} className="flex flex-col gap-3.5" noValidate>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="reset-otp" className="text-xs font-medium text-text">
              6-Digit One-Time Verification Code
            </label>
            <input
              ref={otpRef}
              id="reset-otp"
              name="otp"
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={6}
              required
              value={otpCode}
              onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
              placeholder="123456"
              className="w-full font-mono text-center tracking-widest text-lg px-3 py-2 rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
            />
          </div>

          <PasswordInput
            id="reset-new-password"
            name="newPassword"
            label="New Password"
            hint="Min. 12 characters"
            autoComplete="new-password"
            required
            minLength={12}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />

          <PasswordInput
            id="reset-confirm-password"
            name="confirmPassword"
            label="Confirm New Password"
            autoComplete="new-password"
            required
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />

          <div className="flex flex-col gap-1.5">
            <label htmlFor="reset-totp" className="text-xs font-medium text-text">
              Authenticator 2FA Code <span className="text-text-muted">(Required for Owner/Admin)</span>
            </label>
            <input
              id="reset-totp"
              name="totpCode"
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={6}
              value={totpCode}
              onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, ''))}
              placeholder="Optional if non-privileged role"
              className="w-full font-mono text-center tracking-widest text-sm px-3 py-2 rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
            />
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            aria-busy={isSubmitting}
            className="w-full mt-2 py-2 px-4 bg-accent-solid hover:bg-accent-hover text-white text-xs font-semibold rounded shadow transition-colors flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-surface"
          >
            {isSubmitting ? (
              <span>Updating Password...</span>
            ) : (
              <>
                <ShieldCheck className="w-4 h-4" aria-hidden="true" />
                <span>Confirm & Reset Password</span>
              </>
            )}
          </button>
        </form>
      )}

      {step === 3 && (
        <div className="flex flex-col items-center gap-4 py-4 text-center">
          <div className="w-12 h-12 rounded-full bg-approve-bg text-approve flex items-center justify-center">
            <CheckCircle className="w-8 h-8" aria-hidden="true" />
          </div>
          <div className="flex flex-col gap-1">
            <h2 className="text-base font-semibold text-text">Password Updated</h2>
            <p className="text-xs text-text-muted">
              You can now sign in to the console using your new credentials.
            </p>
          </div>
          <button
            type="button"
            onClick={() => navigate('/login')}
            className="w-full mt-4 py-2 px-4 bg-accent-solid hover:bg-accent-hover text-white text-xs font-semibold rounded shadow transition-colors focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-surface"
          >
            Return to Sign In
          </button>
        </div>
      )}
    </AuthLayout>
  );
};
