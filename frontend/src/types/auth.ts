export type UserRole =
  | 'owner'
  | 'admin'
  | 'risk_manager'
  | 'rule_author'
  | 'risk_analyst'
  | 'auditor'
  | 'developer';

export interface UserProfile {
  id?: string;
  user_id?: string;
  tenant_id?: string;
  role: UserRole;
  email: string;
  mobile?: string;
  full_name: string;
  status?: string;
  totp_enabled?: boolean;
}

export interface AuthContextType {
  user: UserProfile | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  sessionExpired: boolean;
  clearSessionExpired: () => void;
  login: (identifier: string, password: string) => Promise<{ mfaRequired: boolean; mfaToken?: string }>;
  loginChallenge: (mfaToken: string, code: string, isRecoveryCode?: boolean) => Promise<UserProfile>;
  logout: () => Promise<void>;
  refresh: () => Promise<boolean>;
  setSessionUser: (user: UserProfile, token: string) => void;
}

