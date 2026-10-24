import React, { useRef, useEffect, useCallback } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { Pause, Play, AlertCircle, Zap, ShieldAlert } from 'lucide-react';
import { DecisionBadge } from './DecisionBadge';
import { formatTimeIST, formatLatency, formatScore } from '../utils/format';
import type { DecisionState, DecisionAction } from '../state/decisionReducer';
import type { DecisionResponse } from '../types/decision';

interface LiveDecisionsTableProps {
  state: DecisionState;
  dispatch: React.Dispatch<DecisionAction>;
  onOpenTestModal: () => void;
}

export const LiveDecisionsTable: React.FC<LiveDecisionsTableProps> = ({
  state,
  dispatch,
  onOpenTestModal,
}) => {
  const { decisions, isPaused, pausedQueue, selectedDecisionId } = state;
  const parentRef = useRef<HTMLDivElement>(null);

  const rowVirtualizer = useVirtualizer({
    count: decisions.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 36, // Row height: 36px
    overscan: 10,
  });

  const selectedIndex = decisions.findIndex((d) => d.decision_id === selectedDecisionId);

  // Auto-scroll selected row into view when j/k changes selection
  useEffect(() => {
    if (selectedIndex >= 0) {
      rowVirtualizer.scrollToIndex(selectedIndex, { align: 'auto' });
    }
  }, [selectedIndex, rowVirtualizer]);

  // Global j/k and Space keyboard shortcuts
  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      // Ignore if user is typing inside an input, textarea, or select
      const activeTag = (document.activeElement?.tagName || '').toLowerCase();
      if (activeTag === 'input' || activeTag === 'textarea' || activeTag === 'select') {
        return;
      }

      if (e.key === 'j' || e.key === 'ArrowDown') {
        e.preventDefault();
        dispatch({ type: 'SELECT_NEXT' });
      } else if (e.key === 'k' || e.key === 'ArrowUp') {
        e.preventDefault();
        dispatch({ type: 'SELECT_PREV' });
      } else if (e.key === ' ' || e.code === 'Space') {
        // Space toggles stream pause state
        e.preventDefault();
        dispatch({ type: 'TOGGLE_PAUSE' });
      }
    },
    [dispatch]
  );

  useEffect(() => {
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handleKeyDown]);

  const selectedDecision: DecisionResponse | undefined =
    decisions.find((d) => d.decision_id === selectedDecisionId) || decisions[0];

  return (
    <div className="flex flex-col flex-1 h-full min-h-0 bg-canvas text-text">
      {/* Table Toolbar */}
      <div className="flex items-center justify-between px-4 py-2.5 border-b border-border bg-surface">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => dispatch({ type: 'TOGGLE_PAUSE' })}
            className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-sm text-xs font-medium border transition-colors ${
              isPaused
                ? 'bg-amber-500/20 text-amber-300 border-amber-500/40 hover:bg-amber-500/30'
                : 'bg-surface-raised text-text border-border hover:bg-surface-raised/80'
            }`}
          >
            {isPaused ? (
              <>
                <Play className="w-3.5 h-3.5 fill-current" aria-hidden="true" />
                <span>Resume Feed</span>
                {pausedQueue.length > 0 && (
                  <span className="ml-1 px-1.5 py-0.2 bg-amber-400 text-black font-mono font-bold rounded-full text-[10px]">
                    +{pausedQueue.length} new
                  </span>
                )}
              </>
            ) : (
              <>
                <Pause className="w-3.5 h-3.5 fill-current" aria-hidden="true" />
                <span>Pause Stream</span>
              </>
            )}
          </button>

          <span className="text-xs text-text-muted font-mono">
            Buffer: <strong className="text-text tabular-nums">{decisions.length.toLocaleString()}</strong> / 2,000 events
          </span>
        </div>

        <div className="flex items-center gap-3">
          <span className="hidden md:inline text-xs text-text-muted font-mono">
            Navigate: <kbd className="px-1 py-0.5 rounded bg-surface-raised border border-border text-text font-bold">j</kbd> / <kbd className="px-1 py-0.5 rounded bg-surface-raised border border-border text-text font-bold">k</kbd> | Pause: <kbd className="px-1 py-0.5 rounded bg-surface-raised border border-border text-text font-bold">Space</kbd>
          </span>

          <button
            type="button"
            onClick={onOpenTestModal}
            className="inline-flex items-center gap-1.5 px-3 py-1 rounded-sm text-xs font-semibold bg-accent-solid hover:bg-accent-hover text-white shadow-sm transition-colors focus-visible:ring-2 focus-visible:ring-accent"
          >
            <Zap className="w-3.5 h-3.5" aria-hidden="true" />
            <span>Send Test Transaction</span>
          </button>
        </div>
      </div>

      {/* Main Split View: Virtualized Table + Inspection Drawer */}
      <div className="flex flex-1 min-h-0 overflow-hidden">
        {/* Table Container */}
        <div className="flex flex-col flex-1 min-w-0 border-r border-border">
          {/* Table Header */}
          <div className="grid grid-cols-[140px_110px_1fr_90px_100px_90px] px-3 py-2 text-[11px] font-semibold text-text-muted uppercase tracking-wider border-b border-border bg-surface select-none">
            <div>Time (IST)</div>
            <div>Outcome</div>
            <div>Event ID</div>
            <div className="text-right">Risk Score</div>
            <div className="text-center">Hard Override</div>
            <div className="text-right">Latency</div>
          </div>

          {/* Virtualized Body */}
          <div
            ref={parentRef}
            className="flex-1 overflow-y-auto min-h-0 focus:outline-none"
            tabIndex={0}
            role="region"
            aria-label="Real-time Decisions Feed"
          >
            {decisions.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-64 text-center p-6 text-text-muted">
                <AlertCircle className="w-8 h-8 mb-2 stroke-1 text-text-muted" aria-hidden="true" />
                <p className="text-sm font-medium text-text">Waiting for real-time transactions...</p>
                <p className="text-xs text-text-muted mt-1 max-w-sm">
                  Transactions evaluated by the decision engine will appear here via Server-Sent Events.
                  Use the dev button above to post a test event.
                </p>
                <button
                  type="button"
                  onClick={onOpenTestModal}
                  className="mt-4 px-3 py-1.5 text-xs font-medium rounded-sm border border-border bg-surface hover:bg-surface-raised text-text"
                >
                  Send first transaction
                </button>
              </div>
            ) : (
              <div
                style={{
                  height: `${rowVirtualizer.getTotalSize()}px`,
                  width: '100%',
                  position: 'relative',
                }}
              >
                {rowVirtualizer.getVirtualItems().map((virtualRow) => {
                  const decision = decisions[virtualRow.index];
                  const isSelected = decision.decision_id === selectedDecisionId;

                  return (
                    <div
                      key={decision.decision_id}
                      onClick={() =>
                        dispatch({ type: 'SELECT_DECISION', payload: decision.decision_id })
                      }
                      style={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        width: '100%',
                        height: `${virtualRow.size}px`,
                        transform: `translateY(${virtualRow.start}px)`,
                      }}
                      className={`grid grid-cols-[140px_110px_1fr_90px_100px_90px] items-center px-3 text-xs border-b border-border/40 cursor-pointer transition-colors select-none ${
                        isSelected
                          ? 'bg-surface-raised border-l-2 border-l-accent'
                          : 'hover:bg-surface/60'
                      }`}
                      role="row"
                      aria-selected={isSelected}
                    >
                      <div className="font-mono text-text-muted tabular-nums truncate">
                        {formatTimeIST(decision.timestamp)}
                      </div>
                      <div>
                        <DecisionBadge outcome={decision.outcome} size="sm" />
                      </div>
                      <div className="font-mono text-text truncate pr-2 font-medium" title={decision.event_id}>
                        {decision.event_id}
                      </div>
                      <div className="font-mono tabular-nums text-right font-medium">
                        {formatScore(decision.score)}
                      </div>
                      <div className="text-center">
                        {decision.hard_rule_fired ? (
                          <span
                            className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-sm text-[10px] font-bold uppercase bg-red-500/20 text-red-400 border border-red-500/30"
                            title="Hard rule override fired"
                          >
                            <ShieldAlert className="w-3 h-3" aria-hidden="true" />
                            YES
                          </span>
                        ) : (
                          <span className="text-[11px] text-text-muted font-mono">No</span>
                        )}
                      </div>
                      <div className="font-mono text-text-muted tabular-nums text-right">
                        {formatLatency(decision.latency_ms)}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Selected Decision Detail Drawer */}
        <aside
          className="hidden lg:flex flex-col w-96 border-l border-border bg-surface overflow-y-auto p-4 select-text"
          aria-label="Selected Decision Detail"
        >
          {selectedDecision ? (
            <div className="space-y-4">
              <div className="border-b border-border pb-3">
                <div className="text-[11px] font-mono text-text-muted uppercase">Decision ID</div>
                <div className="text-xs font-mono font-semibold text-text break-all mt-0.5">
                  {selectedDecision.decision_id}
                </div>
              </div>

              <div>
                <div className="text-[11px] font-mono text-text-muted uppercase">Evaluation Outcome</div>
                <div className="mt-1 flex items-center gap-2">
                  <DecisionBadge outcome={selectedDecision.outcome} size="md" />
                  <span className="text-xs font-mono tabular-nums text-text-muted">
                    Score: <strong className="text-text">{formatScore(selectedDecision.score)}</strong> / 100
                  </span>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="p-2 rounded bg-surface-raised border border-border">
                  <div className="text-[10px] text-text-muted uppercase font-mono">Event Latency</div>
                  <div className="font-mono text-sm font-semibold tabular-nums mt-0.5">
                    {formatLatency(selectedDecision.latency_ms)}
                  </div>
                </div>
                <div className="p-2 rounded bg-surface-raised border border-border">
                  <div className="text-[10px] text-text-muted uppercase font-mono">Hard Override</div>
                  <div className="font-mono text-sm font-semibold mt-0.5">
                    {selectedDecision.hard_rule_fired ? 'Active (Yes)' : 'None'}
                  </div>
                </div>
              </div>

              <div>
                <div className="text-[11px] font-mono text-text-muted uppercase">Event ID</div>
                <div className="text-xs font-mono text-text break-all mt-0.5 p-1.5 rounded bg-surface-raised border border-border">
                  {selectedDecision.event_id}
                </div>
              </div>

              <div>
                <div className="text-[11px] font-mono text-text-muted uppercase">Ruleset Hash</div>
                <div className="text-[11px] font-mono text-text-muted truncate mt-0.5" title={selectedDecision.ruleset_version_hash}>
                  {selectedDecision.ruleset_id} ({selectedDecision.ruleset_version_hash?.slice(0, 12)}...)
                </div>
              </div>

              <div>
                <div className="text-[11px] font-mono text-text-muted uppercase mb-1.5">
                  Fired Rules ({selectedDecision.rules_fired?.length || 0})
                </div>
                {(!selectedDecision.rules_fired || selectedDecision.rules_fired.length === 0) ? (
                  <div className="text-xs text-text-muted italic p-2 rounded bg-surface-raised border border-border">
                    No individual rules fired for this transaction.
                  </div>
                ) : (
                  <ul className="space-y-1.5">
                    {selectedDecision.rules_fired.map((rule, idx) => (
                      <li
                        key={`${rule.rule_id}-${idx}`}
                        className="p-2 rounded bg-surface-raised border border-border text-xs"
                      >
                        <div className="flex items-center justify-between font-mono font-medium text-text">
                          <span className="truncate">{rule.name || rule.rule_id}</span>
                          <span className="text-amber-400 font-bold tabular-nums ml-2">
                            +{rule.score_impact}
                          </span>
                        </div>
                        <div className="text-[11px] text-text-muted font-mono mt-0.5">
                          Code: {rule.reason_code}
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-center h-full text-xs text-text-muted italic">
              Select a decision row to inspect details
            </div>
          )}
        </aside>
      </div>
    </div>
  );
};
