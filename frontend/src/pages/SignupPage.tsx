import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AuthLayout } from '../components/auth/AuthLayout';
import { PasswordInput } from '../components/auth/PasswordInput';
import { TotpQrCode } from '../components/auth/TotpQrCode';
import { ShieldCheck, UserPlus, Smartphone, CheckCircle } from 'lucide-react';

export const SignupPage: React.FC = () => {
  const navigate = useNavigate();

  // Multi-step: 1 = Registration Info, 2 = Mobile OTP, 3 = TOTP Setup, 4 = Complete
  const [step, setStep] = useState<1 | 2 | 3 | 4>(1);

  // Step 1: Registration Form
  const [orgName, setOrgName] = useState('');
  const [ownerName, setOwnerName] = useState('');
  const [email, setEmail] = useState('');
  const [mobile, setMobile] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // Step 2: Mobile OTP
  const [challengeId, setChallengeId] = useState<string | null>(null);
  const [otpCode, setOtpCode] = useState('');

  // Step 3: TOTP Setup
  const [setupToken, setSetupToken] = useState<string | null>(null);
  const [totpSecret, setTotpSecret] = useState('');
  const [otpauthUri, setOtpauthUri] = useState('');
  const [verifyTotpCode, setVerifyTotpCode] = useState('');

  // Submission & Error handling
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Focus management
  const orgNameRef = useRef<HTMLInputElement>(null);
  const otpInputRef = useRef<HTMLInputElement>(null);
  const totpInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (step === 1 && orgNameRef.current) {
      orgNameRef.current.focus();
    } else if (step === 2 && otpInputRef.current) {
      otpInputRef.current.focus();
    } else if (step === 3 && totpInputRef.current) {
      totpInputRef.current.focus();
    }
  }, [step]);

  // Handle Step 1: Submit Details & Request OTP
  const handleInitiateSignup = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;
    setErrorMessage(null);

    // Validation
    if (!orgName.trim() || !ownerName.trim() || !email.trim() || !mobile.trim()) {
      setErrorMessage('All fields are required.');
      return;
    }
    if (password.length < 12) {
      setErrorMessage('Password must be at least 12 characters.');
      return;
    }
    if (password !== confirmPassword) {
      setErrorMessage('Passwords do not match.');
      return;
    }

    setIsSubmitting(true);
    try {
      const res = await fetch('/v1/auth/signup/initiate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          organization_name: orgName.trim(),
          owner_name: ownerName.trim(),
          email: email.trim().toLowerCase(),
          mobile: mobile.trim(),
          password,
        }),
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || err.detail || 'Signup initiation failed.');
      }

      const data = await res.json();
      setChallengeId(data.challenge_id);
      setStep(2);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Signup initiation failed.';
      setErrorMessage(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle Step 2: Verify Mobile OTP
  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting || !challengeId) return;
    setErrorMessage(null);

    if (otpCode.length !== 6) {
      setErrorMessage('Please enter the 6-digit OTP sent to your mobile.');
      return;
    }

    setIsSubmitting(true);
    try {
      const res = await fetch('/v1/auth/signup/verify-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          challenge_id: challengeId,
          otp_code: otpCode.trim(),
        }),
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || err.detail || 'OTP verification failed.');
      }

      const data = await res.json();
      const token = data.setup_token as string;
      setSetupToken(token);

      // Now fetch TOTP setup parameters using setup token
      const setupRes = await fetch('/v1/auth/totp/setup', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      if (!setupRes.ok) {
        throw new Error('Failed to initialize TOTP credentials.');
      }

      const setupData = await setupRes.json();
      setTotpSecret(setupData.secret_key || setupData.secret || '');
      setOtpauthUri(setupData.otpauth_uri || '');
      setStep(3);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'OTP verification failed.';
      setErrorMessage(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Handle Step 3: Verify TOTP Code
  const handleVerifyTotp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting || !setupToken) return;
    setErrorMessage(null);

    if (verifyTotpCode.length !== 6) {
      setErrorMessage('Please enter the 6-digit code from your authenticator app.');
      return;
    }

    setIsSubmitting(true);
    try {
      const res = await fetch('/v1/auth/totp/verify', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${setupToken}`,
        },
        body: JSON.stringify({
          totp_code: verifyTotpCode.trim(),
        }),
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || err.detail || 'Invalid TOTP code.');
      }

      setStep(4);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Invalid TOTP code.';
      setErrorMessage(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title={
        step === 1
          ? 'Register Organization'
          : step === 2
            ? 'Verify Mobile Number'
            : step === 3
              ? 'Configure Two-Factor Authentication'
              : 'Registration Complete'
      }
      subtitle={
        step === 1
          ? 'Create the primary tenant and initial owner account for Project Aegis.'
          : step === 2
            ? `Enter the 6-digit SMS verification code sent to ${mobile}.`
            : step === 3
              ? 'Scan the QR code with your authenticator app (Google Authenticator, 1Password, etc.).'
              : 'Your organization and owner credentials have been established.'
      }
    >
      {/* Global Error Banner */}
      {errorMessage && (
        <div
          role="alert"
          aria-live="assertive"
          className="p-3 bg-decline-bg border border-decline text-decline-text rounded-md text-xs flex items-center gap-2"
        >
          <span>{errorMessage}</span>
        </div>
      )}

      {step === 1 && (
        /* STEP 1: ORGANIZATION & OWNER CREDENTIALS */
        <form onSubmit={handleInitiateSignup} className="flex flex-col gap-3.5" noValidate>
          <div className="flex flex-col gap-1">
            <label htmlFor="signup-org-name" className="text-xs font-medium text-text">
              Organization Name
            </label>
            <input
              ref={orgNameRef}
              id="signup-org-name"
              name="organizationName"
              type="text"
              required
              value={orgName}
              onChange={(e) => setOrgName(e.target.value)}
              placeholder="Acme Financial Services"
              className="w-full px-3 py-2 text-xs rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="signup-owner-name" className="text-xs font-medium text-text">
              Primary Owner Full Name
            </label>
            <input
              id="signup-owner-name"
              name="ownerName"
              type="text"
              required
              value={ownerName}
              onChange={(e) => setOwnerName(e.target.value)}
              placeholder="Jane Doe"
              className="w-full px-3 py-2 text-xs rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="flex flex-col gap-1">
              <label htmlFor="signup-email" className="text-xs font-medium text-text">
                Work Email
              </label>
              <input
                id="signup-email"
                name="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="jane@acmepay.com"
                className="w-full px-3 py-2 text-xs rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
              />
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="signup-mobile" className="text-xs font-medium text-text">
                Mobile (E.164)
              </label>
              <input
                id="signup-mobile"
                name="mobile"
                type="tel"
                autoComplete="tel"
                required
                value={mobile}
                onChange={(e) => setMobile(e.target.value)}
                placeholder="+919876543210"
                className="w-full px-3 py-2 text-xs rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
              />
            </div>
          </div>

          <PasswordInput
            id="signup-password"
            name="password"
            label="Master Password"
            hint="Min. 12 characters"
            autoComplete="new-password"
            required
            minLength={12}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />

          <PasswordInput
            id="signup-confirm-password"
            name="confirmPassword"
            label="Confirm Password"
            autoComplete="new-password"
            required
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />

          <div className="flex justify-between items-center text-xs mt-1">
            <Link
              to="/login"
              className="text-text-muted hover:text-text underline focus:outline-none focus:ring-1 focus:ring-accent rounded px-1"
            >
              Already registered? Sign in
            </Link>
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            aria-busy={isSubmitting}
            className="w-full mt-2 py-2 px-4 bg-accent-solid hover:bg-accent-hover text-white text-xs font-semibold rounded shadow transition-colors flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-surface"
          >
            {isSubmitting ? (
              <span>Initiating Registration...</span>
            ) : (
              <>
                <UserPlus className="w-4 h-4" aria-hidden="true" />
                <span>Next: Mobile Verification</span>
              </>
            )}
          </button>
        </form>
      )}

      {step === 2 && (
        /* STEP 2: MOBILE OTP */
        <form onSubmit={handleVerifyOtp} className="flex flex-col gap-4" noValidate>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="signup-otp" className="text-xs font-medium text-text">
              6-Digit Mobile Verification Code
            </label>
            <input
              ref={otpInputRef}
              id="signup-otp"
              name="otp"
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={6}
              autoComplete="one-time-code"
              required
              value={otpCode}
              onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
              placeholder="123456"
              className="w-full font-mono text-center tracking-widest text-lg px-3 py-2 rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
            />
          </div>

          <div className="flex justify-between items-center text-xs">
            <button
              type="button"
              onClick={() => {
                setStep(1);
                setErrorMessage(null);
              }}
              className="text-text-muted hover:text-text underline focus:outline-none focus:ring-1 focus:ring-accent rounded px-1"
            >
              Back to details
            </button>
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            aria-busy={isSubmitting}
            className="w-full mt-2 py-2 px-4 bg-accent-solid hover:bg-accent-hover text-white text-xs font-semibold rounded shadow transition-colors flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-surface"
          >
            {isSubmitting ? (
              <span>Verifying OTP...</span>
            ) : (
              <>
                <Smartphone className="w-4 h-4" aria-hidden="true" />
                <span>Verify Mobile & Setup 2FA</span>
              </>
            )}
          </button>
        </form>
      )}

      {step === 3 && (
        /* STEP 3: TOTP SETUP */
        <form onSubmit={handleVerifyTotp} className="flex flex-col gap-4" noValidate>
          <TotpQrCode secret={totpSecret} otpauthUri={otpauthUri} />

          <div className="flex flex-col gap-1.5">
            <label htmlFor="signup-totp-verify" className="text-xs font-medium text-text">
              Enter 6-Digit Code from Authenticator App
            </label>
            <input
              ref={totpInputRef}
              id="signup-totp-verify"
              name="totpCode"
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={6}
              required
              value={verifyTotpCode}
              onChange={(e) => setVerifyTotpCode(e.target.value.replace(/\D/g, ''))}
              placeholder="123456"
              className="w-full font-mono text-center tracking-widest text-lg px-3 py-2 rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
            />
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            aria-busy={isSubmitting}
            className="w-full mt-2 py-2 px-4 bg-accent-solid hover:bg-accent-hover text-white text-xs font-semibold rounded shadow transition-colors flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-surface"
          >
            {isSubmitting ? (
              <span>Verifying & Finalizing...</span>
            ) : (
              <>
                <ShieldCheck className="w-4 h-4" aria-hidden="true" />
                <span>Complete Registration</span>
              </>
            )}
          </button>
        </form>
      )}

      {step === 4 && (
        /* STEP 4: REGISTRATION COMPLETE */
        <div className="flex flex-col items-center gap-4 py-4 text-center">
          <div className="w-12 h-12 rounded-full bg-approve-bg text-approve flex items-center justify-center">
            <CheckCircle className="w-8 h-8" aria-hidden="true" />
          </div>
          <div className="flex flex-col gap-1">
            <h2 className="text-base font-semibold text-text">Account Ready</h2>
            <p className="text-xs text-text-muted">
              Your organization has been created and two-factor authentication is configured.
            </p>
          </div>
          <button
            type="button"
            onClick={() => navigate('/login')}
            className="w-full mt-4 py-2 px-4 bg-accent-solid hover:bg-accent-hover text-white text-xs font-semibold rounded shadow transition-colors focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-surface"
          >
            Sign In with Master Password
          </button>
        </div>
      )}
    </AuthLayout>
  );
};
