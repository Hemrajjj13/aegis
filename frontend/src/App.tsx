import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import { RoleGuard } from './routes/RoleGuard';
import { RootRedirect } from './routes/RootRedirect';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { InviteAcceptPage } from './pages/InviteAcceptPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { DecisionsPage } from './pages/DecisionsPage';
import { NotAvailableYetPage } from './pages/NotAvailableYetPage';

// Dev-only mailbox dynamically loaded strictly during development
const DevMailboxPage = import.meta.env.DEV
  ? React.lazy(() => import('./pages/DevMailboxPage').then(m => ({ default: m.DevMailboxPage })))
  : null;

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public Authentication Routes */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/invite/accept" element={<InviteAcceptPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />

          {/* Dev-only Mailbox (Tier 0 / Tier 1, tree-shaken in production) */}
          {import.meta.env.DEV && DevMailboxPage && (
            <Route
              path="/dev/mailbox"
              element={
                <React.Suspense fallback={null}>
                  <DevMailboxPage />
                </React.Suspense>
              }
            />
          )}

          {/* Root dynamic redirect */}
          <Route path="/" element={<RootRedirect />} />

          {/* Protected Live Decisions Feed */}
          <Route
            path="/decisions"
            element={
              <RoleGuard>
                <DecisionsPage />
              </RoleGuard>
            }
          />

          {/* Role-Based Landing Placeholders (Milestone M8) */}
          <Route
            path="/settings"
            element={
              <RoleGuard allowedRoles={['owner']}>
                <NotAvailableYetPage featureName="Tenant Administration & Governance" />
              </RoleGuard>
            }
          />

          <Route
            path="/settings/members"
            element={
              <RoleGuard allowedRoles={['owner', 'admin']}>
                <NotAvailableYetPage featureName="Tenant User Management" />
              </RoleGuard>
            }
          />

          <Route
            path="/rules"
            element={
              <RoleGuard allowedRoles={['owner', 'admin', 'risk_manager', 'rule_author']}>
                <NotAvailableYetPage featureName="CEL Rule Authoring & Simulation" />
              </RoleGuard>
            }
          />

          <Route
            path="/cases"
            element={
              <RoleGuard allowedRoles={['owner', 'admin', 'risk_manager', 'risk_analyst']}>
                <NotAvailableYetPage featureName="Manual Review Case Queue" />
              </RoleGuard>
            }
          />

          <Route
            path="/audit"
            element={
              <RoleGuard allowedRoles={['owner', 'admin', 'auditor']}>
                <NotAvailableYetPage featureName="Cryptographic Audit Ledger & Sealing" />
              </RoleGuard>
            }
          />

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
};

export default App;
