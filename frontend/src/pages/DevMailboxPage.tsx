import React, { useState, useEffect } from 'react';
import { AuthLayout } from '../components/auth/AuthLayout';
import { Mail, RefreshCw, Copy, Check, ExternalLink } from 'lucide-react';

interface MailboxItem {
  id: string;
  destination: string;
  purpose: string;
  code?: string;
  link?: string;
  expiresAt: string;
  createdAt: string;
}

export const DevMailboxPage: React.FC = () => {
  const [items, setItems] = useState<MailboxItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const fetchMailbox = async () => {
    setIsLoading(true);
    try {
      const res = await fetch('/dev/mailbox');
      if (res.ok) {
        const data = await res.json();
        setItems(data);
      }
    } catch {
      // Dev mailbox error handling
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchMailbox();
    const interval = setInterval(fetchMailbox, 3000);
    return () => clearInterval(interval);
  }, []);

  const handleCopy = (id: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  return (
    <AuthLayout
      title="Development Simulated Mailbox"
      subtitle="In-memory notification delivery for local development (Tier 0 & Tier 1 only)."
    >
      <div
        role="alert"
        className="p-3 mb-3 rounded bg-amber-950/40 border border-amber-600/40 text-amber-200 text-xs font-mono flex items-center gap-2"
      >
        <span className="font-bold uppercase tracking-wider text-[10px] px-1.5 py-0.5 rounded bg-amber-600/30 border border-amber-500/50">
          DEV ONLY
        </span>
        <span>Simulated mailbox for local development. Not available in production.</span>
      </div>

      <div className="flex justify-between items-center pb-2 border-b border-border">
        <span className="text-xs font-mono text-text-muted">
          Active Messages: {items.length}
        </span>
        <button
          type="button"
          onClick={fetchMailbox}
          disabled={isLoading}
          className="px-2.5 py-1 text-xs rounded border border-border bg-surface hover:bg-surface-raised text-text flex items-center gap-1.5 transition-colors focus:outline-none focus:ring-1 focus:ring-accent"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} aria-hidden="true" />
          <span>Refresh</span>
        </button>
      </div>

      <div className="flex flex-col gap-3 max-h-[450px] overflow-y-auto pr-1">
        {items.length === 0 ? (
          <div className="p-8 text-center text-xs text-text-muted flex flex-col items-center gap-2">
            <Mail className="w-8 h-8 opacity-40" aria-hidden="true" />
            <span>No notifications dispatched yet.</span>
          </div>
        ) : (
          items.map((item) => (
            <div
              key={item.id}
              className="p-3 rounded border border-border bg-surface-raised flex flex-col gap-2 text-xs"
            >
              <div className="flex justify-between items-start">
                <div className="flex flex-col">
                  <span className="font-semibold text-text font-mono">
                    {item.purpose}
                  </span>
                  <span className="text-[11px] text-text-muted">
                    To: {item.destination}
                  </span>
                </div>
                <span className="text-[10px] font-mono text-text-muted">
                  {new Date(item.createdAt).toLocaleTimeString()}
                </span>
              </div>

              {item.code && (
                <div className="flex items-center justify-between p-2 rounded bg-surface border border-border">
                  <span className="text-[11px] text-text-muted">OTP Code:</span>
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-sm font-bold text-accent tracking-widest">
                      {item.code}
                    </span>
                    <button
                      type="button"
                      onClick={() => handleCopy(item.id, item.code!)}
                      className="p-1 text-text-muted hover:text-text rounded transition-colors"
                      aria-label="Copy verification code"
                    >
                      {copiedId === item.id ? (
                        <Check className="w-3.5 h-3.5 text-approve" />
                      ) : (
                        <Copy className="w-3.5 h-3.5" />
                      )}
                    </button>
                  </div>
                </div>
              )}

              {item.link && (
                <div className="flex flex-col gap-1.5 p-2 rounded bg-surface border border-border">
                  <div className="flex justify-between items-center">
                    <span className="text-[11px] text-text-muted">Invite Link:</span>
                    <button
                      type="button"
                      onClick={() => handleCopy(item.id, item.link!)}
                      className="text-[11px] text-accent flex items-center gap-1 hover:underline"
                    >
                      {copiedId === item.id ? <Check className="w-3 h-3 text-approve" /> : <Copy className="w-3 h-3" />}
                      <span>Copy link</span>
                    </button>
                  </div>
                  <a
                    href={item.link}
                    className="text-[11px] font-mono text-accent hover:underline truncate flex items-center gap-1"
                  >
                    <span>{item.link}</span>
                    <ExternalLink className="w-3 h-3 shrink-0" aria-hidden="true" />
                  </a>
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </AuthLayout>
  );
};

