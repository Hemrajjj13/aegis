import React, { useReducer, useState } from 'react';
import { Shield } from 'lucide-react';
import { LiveDecisionsTable } from './components/LiveDecisionsTable';
import { ConnectionStatusIndicator } from './components/ConnectionStatusIndicator';
import { ThemeToggle } from './components/ThemeToggle';
import { TestTransactionModal } from './components/TestTransactionModal';
import { decisionReducer, createInitialState } from './state/decisionReducer';
import { useDecisionStream } from './hooks/useDecisionStream';
import { DEV_TENANT_ID } from './api/client';

export const App: React.FC = () => {
  const [state, dispatch] = useReducer(decisionReducer, false, createInitialState);
  const [isTestModalOpen, setIsTestModalOpen] = useState<boolean>(false);

  // Connect to live decision SSE feed
  const { status } = useDecisionStream({ dispatch });

  return (
    <div className="flex flex-col h-screen w-screen overflow-hidden bg-canvas text-text">
      {/* Top Application Header */}
      <header className="flex items-center justify-between px-4 h-12 border-b border-border bg-surface shrink-0 select-none">
        {/* Brand & Page Identity */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <Shield className="w-5 h-5 text-accent" aria-hidden="true" />
            <span className="font-bold text-sm tracking-wider uppercase font-mono text-text">
              AEGIS
            </span>
          </div>

          <div className="h-4 w-[1px] bg-border" aria-hidden="true" />

          <h1 className="text-xs font-semibold uppercase tracking-wider text-text">
            Live Decisions
          </h1>

          <span
            className="hidden sm:inline-flex items-center px-2 py-0.5 rounded-sm text-[10px] font-mono border border-border bg-surface-raised text-text-muted"
            title="Active Development Tenant ID"
          >
            Tenant: {DEV_TENANT_ID.slice(0, 8)}... (dev)
          </span>
        </div>

        {/* Global Controls & Status */}
        <div className="flex items-center gap-3">
          <ConnectionStatusIndicator status={status} />
          <ThemeToggle />
        </div>
      </header>

      {/* Main Content: Live Decisions Virtualized Table */}
      <main className="flex-1 flex flex-col min-h-0 overflow-hidden">
        <LiveDecisionsTable
          state={state}
          dispatch={dispatch}
          onOpenTestModal={() => setIsTestModalOpen(true)}
        />
      </main>

      {/* Dev-only Test Transaction Modal */}
      <TestTransactionModal
        isOpen={isTestModalOpen}
        onClose={() => setIsTestModalOpen(false)}
        onSuccess={() => {
          // The decision will arrive naturally over SSE stream
        }}
      />
    </div>
  );
};

export default App;
