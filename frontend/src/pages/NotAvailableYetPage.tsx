import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { Shield, LogOut, ArrowRight, Clock } from 'lucide-react';
import { ThemeToggle } from '../components/ThemeToggle';

interface NotAvailableYetPageProps {
  featureName: string;
  description?: string;
}

export const NotAvailableYetPage: React.FC<NotAvailableYetPageProps> = ({
  featureName,
  description = 'This module is scheduled for implementation in upcoming Milestone M8. The real-time telemetry console is currently available.',
}) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen w-screen flex flex-col bg-canvas text-text">
      {/* Top Header */}
      <header className="flex items-center justify-between px-6 h-12 border-b border-border bg-surface shrink-0 select-none">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <Shield className="w-5 h-5 text-accent" aria-hidden="true" />
            <span className="font-bold text-sm tracking-wider uppercase font-mono text-text">
              AEGIS
            </span>
          </div>

          <div className="h-4 w-[1px] bg-border" aria-hidden="true" />

          <span className="text-xs font-semibold uppercase tracking-wider text-text">
            {featureName}
          </span>

          {user && (
            <span className="hidden sm:inline-flex items-center px-2 py-0.5 rounded-sm text-[10px] font-mono border border-border bg-surface-raised text-text-muted">
              Role: {user.role.toUpperCase()}
            </span>
          )}
        </div>

        <div className="flex items-center gap-3">
          <ThemeToggle />
          <button
            type="button"
            onClick={handleLogout}
            className="flex items-center gap-1.5 px-2.5 py-1 text-xs rounded border border-border bg-surface hover:bg-surface-raised text-text transition-colors focus:outline-none focus:ring-1 focus:ring-accent"
          >
            <LogOut className="w-3.5 h-3.5" aria-hidden="true" />
            <span>Sign Out</span>
          </button>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 flex items-center justify-center p-6">
        <div className="max-w-md w-full bg-surface border border-border rounded-lg shadow-xl p-8 flex flex-col items-center text-center gap-6">
          <div className="w-14 h-14 rounded-full bg-accent/10 border border-accent/20 flex items-center justify-center text-accent">
            <Clock className="w-8 h-8" aria-hidden="true" />
          </div>

          <div className="flex flex-col gap-2">
            <h1 className="text-lg font-semibold text-text tracking-tight">
              {featureName} — Not Available Yet
            </h1>
            <p className="text-xs text-text-muted leading-relaxed">
              {description}
            </p>
          </div>

          {user && (
            <div className="w-full p-3 rounded border border-border bg-surface-raised text-left flex flex-col gap-1 text-[11px] font-mono text-text-muted">
              <div className="flex justify-between">
                <span>User:</span>
                <span className="text-text">{user.full_name} ({user.email})</span>
              </div>
              <div className="flex justify-between">
                <span>Tenant:</span>
                <span className="text-text">
                  {user.tenant_id ? `${user.tenant_id.slice(0, 8)}...` : 'N/A'}
                </span>
              </div>
              <div className="flex justify-between">
                <span>Role:</span>
                <span className="text-accent uppercase font-bold">{user.role}</span>
              </div>
            </div>
          )}

          <div className="flex flex-col sm:flex-row gap-3 w-full">
            <button
              type="button"
              onClick={() => navigate('/decisions')}
              className="flex-1 py-2 px-4 bg-accent-solid hover:bg-accent-hover text-white text-xs font-semibold rounded shadow transition-colors flex items-center justify-center gap-2 focus:outline-none focus:ring-2 focus:ring-accent"
            >
              <span>View Live Decisions</span>
              <ArrowRight className="w-3.5 h-3.5" aria-hidden="true" />
            </button>
            <button
              type="button"
              onClick={handleLogout}
              className="py-2 px-4 border border-border bg-surface hover:bg-surface-raised text-text text-xs font-semibold rounded transition-colors focus:outline-none focus:ring-1 focus:ring-accent"
            >
              Sign Out
            </button>
          </div>
        </div>
      </main>
    </div>
  );
};

