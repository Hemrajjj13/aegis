import React, { ReactNode } from 'react';
import { Shield } from 'lucide-react';
import { ThemeToggle } from '../ThemeToggle';

interface AuthLayoutProps {
  children: ReactNode;
  title: string;
  subtitle?: string;
  banner?: ReactNode;
}

export const AuthLayout: React.FC<AuthLayoutProps> = ({
  children,
  title,
  subtitle,
  banner,
}) => {
  return (
    <div className="min-h-screen w-full flex flex-col justify-between bg-canvas text-text selection:bg-accent/30 selection:text-text">
      {/* Top Header */}
      <header className="flex items-center justify-between px-6 py-4 border-b border-border/50 bg-surface/50 backdrop-blur-sm">
        <a
          href="/"
          className="flex items-center gap-2 group focus:outline-none focus:ring-1 focus:ring-accent rounded px-1 py-0.5"
          aria-label="Aegis Decision Engine Home"
        >
          <Shield className="w-5 h-5 text-accent transition-transform group-hover:scale-105" aria-hidden="true" />
          <span className="font-bold text-sm tracking-wider uppercase font-mono text-text">
            AEGIS
          </span>
        </a>
        <div className="flex items-center gap-4">
          {import.meta.env.DEV && (
            <a
              href="/dev/mailbox"
              className="text-xs font-mono text-text-muted hover:text-accent underline px-2 py-1 rounded focus:outline-none focus:ring-1 focus:ring-accent"
            >
              Dev Mailbox
            </a>
          )}
          <ThemeToggle />
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col items-center justify-center p-4 sm:p-6">
        <div className="w-full max-w-md flex flex-col gap-4">
          {banner}

          <div className="bg-surface border border-border rounded-lg shadow-xl p-6 sm:p-8 flex flex-col gap-6">
            <div className="flex flex-col gap-1.5 text-center">
              <h1 className="text-xl font-semibold tracking-tight text-text">
                {title}
              </h1>
              {subtitle && (
                <p className="text-xs text-text-muted leading-relaxed">
                  {subtitle}
                </p>
              )}
            </div>

            {children}
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="py-4 px-6 text-center text-xs text-text-muted border-t border-border/30 bg-surface/30">
        <p className="font-mono text-[11px]">
          Project Aegis &bull; Real-Time Transaction Decision Platform &bull; ISO-20022 / UPI / Card
        </p>
      </footer>
    </div>
  );
};

