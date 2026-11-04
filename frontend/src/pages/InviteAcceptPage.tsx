import React, { useState, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { AuthLayout } from '../components/auth/AuthLayout';
import { PasswordInput } from '../components/auth/PasswordInput';
import { TotpQrCode } from '../components/auth/TotpQrCode';
import { generateBase32Secret, buildOtpAuthUri } from '../utils/totpHelper';
import { useAuth, getDefaultLandingRoute, UserProfile } from '../contexts/AuthContext';
import { UserCheck, AlertTriangle } from 'lucide-react';

export const InviteAcceptPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { setSessionUser } = useAuth();
  const token = searchParams.get('token') || '';

  // Verification state
  const [isVerifying, setIsVerifying] = useState(true);
  const [isValidToken, setIsValidToken] = useState(false);
  const [invitedEmail, setInvitedEmail] = useState('');
  const [invitedRole, setInvitedRole] = useState('');
  const [tenantName, setTenantName] = useState('');

  // Form state
  const [fullName, setFullName] = useState('');
  const [mobile, setMobile] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [totpSecret, setTotpSecret] = useState('');
  const [otpauthUri, setOtpauthUri] = useState('');
  const [totpCode, setTotpCode] = useState('');

  // UI state
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fullNameRef = useRef<HTMLInputElement>(null);

  // Verify token on mount
  useEffect(() => {
    let isMounted = true;
    if (!token) {
      setIsVerifying(false);
      setIsValidToken(false);
      return;
    }

    (async () => {
      try {
        const res = await fetch(`/v1/auth/invite/verify?token=${encodeURIComponent(token)}`);
        const data = await res.json();
        if (isMounted) {
          if (res.ok && data.valid) {
            setIsValidToken(true);
            setInvitedEmail(data.email);
            setInvitedRole(data.role);
            setTenantName(data.tenant_name || 'Aegis Tenant');

            // Generate TOTP secret and URI for the user
            const secret = generateBase32Secret();
            setTotpSecret(secret);
            setOtpauthUri(buildOtpAuthUri(secret, data.email, 'Aegis'));
          } else {
            setIsValidToken(false);
          }
        }
      } catch {
        if (isMounted) {
          setIsValidToken(false);
        }
      } finally {
        if (isMounted) {
          setIsVerifying(false);
        }
      }
    })();

    return () => {
      isMounted = false;
    };
  }, [token]);

  useEffect(() => {
    if (isValidToken && fullNameRef.current) {
      fullNameRef.current.focus();
    }
  }, [isValidToken]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;
    setErrorMessage(null);

    if (!fullName.trim() || !mobile.trim()) {
      setErrorMessage('Please fill in your full name and mobile number.');
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
    if (totpCode.length !== 6) {
      setErrorMessage('Please enter the 6-digit TOTP code from your authenticator app.');
      return;
    }

    setIsSubmitting(true);
    try {
      const res = await fetch('/v1/auth/invite/accept', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          token,
          full_name: fullName.trim(),
          mobile: mobile.trim(),
          password,
          totp_secret: totpSecret,
          totp_code: totpCode.trim(),
        }),
        credentials: 'include',
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || err.detail || 'Invitation acceptance failed.');
      }

      const data = await res.json();
      const accessToken = data.access_token as string;
      const userProfile = data.user as UserProfile;

      setSessionUser(userProfile, accessToken);
      navigate(getDefaultLandingRoute(userProfile.role), { replace: true });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Invitation acceptance failed.';
      setErrorMessage(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isVerifying) {
    return (
      <AuthLayout title="Verifying Invitation..." subtitle="Please wait while we validate your invitation token.">
        <div className="flex justify-center p-8 text-xs text-text-muted">
          <span>Validating token with security server...</span>
        </div>
      </AuthLayout>
    );
  }

  if (!isValidToken) {
    return (
      <AuthLayout
        title="Invalid or Expired Invitation"
        subtitle="This invitation link is invalid, has expired, or has already been accepted."
      >
        <div className="flex flex-col items-center gap-4 py-4 text-center">
          <div className="w-12 h-12 rounded-full bg-decline-bg text-decline-text flex items-center justify-center">
            <AlertTriangle className="w-7 h-7" aria-hidden="true" />
          </div>
          <p className="text-xs text-text-muted">
            Invitations are strictly single-use and expire within 24 hours. Please contact your tenant administrator to request a new invitation link.
          </p>
          <Link
            to="/login"
            className="w-full mt-4 py-2 px-4 bg-surface-raised hover:bg-surface border border-border text-text text-xs font-semibold rounded shadow transition-colors text-center"
          >
            Return to Sign In
          </Link>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title={`Join ${tenantName}`}
      subtitle={`Accept your invitation as ${invitedRole.replace('_', ' ').toUpperCase()} and establish your account.`}
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

      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {/* Email read-only */}
        <div className="flex flex-col gap-1">
          <label htmlFor="invite-email" className="text-xs font-medium text-text">
            Invited Email Address
          </label>
          <input
            id="invite-email"
            name="email"
            type="email"
            readOnly
            value={invitedEmail}
            className="w-full px-3 py-2 text-xs rounded border border-border bg-surface-raised text-text-muted font-mono select-all cursor-not-allowed"
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div className="flex flex-col gap-1">
            <label htmlFor="invite-name" className="text-xs font-medium text-text">
              Your Full Name
            </label>
            <input
              ref={fullNameRef}
              id="invite-name"
              name="fullName"
              type="text"
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Jane Doe"
              className="w-full px-3 py-2 text-xs rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
            />
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="invite-mobile" className="text-xs font-medium text-text">
              Mobile Number (E.164)
            </label>
            <input
              id="invite-mobile"
              name="mobile"
              type="tel"
              required
              value={mobile}
              onChange={(e) => setMobile(e.target.value)}
              placeholder="+919876543210"
              className="w-full px-3 py-2 text-xs rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
            />
          </div>
        </div>

        <PasswordInput
          id="invite-password"
          name="password"
          label="Account Password"
          hint="Min. 12 characters"
          autoComplete="new-password"
          required
          minLength={12}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        <PasswordInput
          id="invite-confirm-password"
          name="confirmPassword"
          label="Confirm Password"
          autoComplete="new-password"
          required
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
        />

        {/* 2FA Setup Section */}
        <div className="border-t border-border pt-4 mt-2 flex flex-col gap-2">
          <span className="text-xs font-semibold text-text uppercase tracking-wider">
            Mandatory Two-Factor Setup
          </span>
          <p className="text-[11px] text-text-muted">
            Scan this QR code in your authenticator app and enter the 6-digit code below to confirm setup.
          </p>

          <TotpQrCode secret={totpSecret} otpauthUri={otpauthUri} />

          <div className="flex flex-col gap-1.5 mt-2">
            <label htmlFor="invite-totp" className="text-xs font-medium text-text">
              Authenticator 6-Digit Code
            </label>
            <input
              id="invite-totp"
              name="totpCode"
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={6}
              required
              value={totpCode}
              onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, ''))}
              placeholder="123456"
              className="w-full font-mono text-center tracking-widest text-lg px-3 py-2 rounded border border-border bg-surface text-text placeholder:text-text-muted/60 focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          aria-busy={isSubmitting}
          className="w-full mt-2 py-2 px-4 bg-accent-solid hover:bg-accent-hover text-white text-xs font-semibold rounded shadow transition-colors flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2 focus:ring-offset-surface"
        >
          {isSubmitting ? (
            <span>Activating Account...</span>
          ) : (
            <>
              <UserCheck className="w-4 h-4" aria-hidden="true" />
              <span>Accept Invitation & Activate</span>
            </>
          )}
        </button>
      </form>
    </AuthLayout>
  );
};