import React from 'react';
import type { StreamConnectionStatus } from '../types/decision';

interface ConnectionStatusIndicatorProps {
  status: StreamConnectionStatus;
  className?: string;
}

export const ConnectionStatusIndicator: React.FC<ConnectionStatusIndicatorProps> = ({
  status,
  className = '',
}) => {
  const config = {
    connected: {
      color: 'bg-[var(--approve)]',
      label: 'Live Stream',
      aria: 'Stream Status: Connected',
      pulse: true,
    },
    connecting: {
      color: 'bg-[var(--review)]',
      label: 'Connecting...',
      aria: 'Stream Status: Connecting',
      pulse: true,
    },
    reconnecting: {
      color: 'bg-[var(--review)]',
      label: 'Reconnecting...',
      aria: 'Stream Status: Reconnecting with backoff',
      pulse: true,
    },
    disconnected: {
      color: 'bg-[var(--decline)]',
      label: 'Disconnected',
      aria: 'Stream Status: Disconnected',
      pulse: false,
    },
  }[status] || {
    color: 'bg-text-muted',
    label: status,
    aria: `Stream Status: ${status}`,
    pulse: false,
  };

  return (
    <div
      role="status"
      aria-live="polite"
      aria-label={config.aria}
      className={`inline-flex items-center gap-2 px-2.5 py-1 rounded-sm text-xs font-mono border border-border bg-surface ${className}`}
    >
      <span className="relative flex h-2 w-2">
        {config.pulse && (
          <span
            className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${config.color}`}
          />
        )}
        <span className={`relative inline-flex rounded-full h-2 w-2 ${config.color}`} />
      </span>
      <span className="text-text font-medium text-[11px] uppercase tracking-wider">
        {config.label}
      </span>
    </div>
  );
};
