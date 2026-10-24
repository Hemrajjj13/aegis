import React from 'react';
import { Check, Flag, X } from 'lucide-react';
import type { DecisionOutcome } from '../types/decision';

interface DecisionBadgeProps {
  outcome: DecisionOutcome | string;
  className?: string;
  size?: 'sm' | 'md';
}

/**
 * Accessible Decision Badge Pill.
 * 
 * Invariant per DESIGN.md §1 & §4:
 * NEVER rely on colour alone for accessibility.
 * Every badge combines:
 * 1. Geometric iconography ([✓] Approve, [⚑] Review, [✕] Decline)
 * 2. Explicit bold uppercase text label
 * 3. Mandatory aria-label="Outcome: <Outcome>"
 * 4. High-contrast WCAG 2.1 AA/AAA compliant CSS variable tokens
 */
export const DecisionBadge: React.FC<DecisionBadgeProps> = ({
  outcome,
  className = '',
  size = 'sm',
}) => {
  const normalized = (outcome || '').toUpperCase();

  const sizeClasses =
    size === 'sm'
      ? 'px-2 py-0.5 text-[11px] gap-1'
      : 'px-2.5 py-1 text-xs gap-1.5';

  switch (normalized) {
    case 'APPROVE':
      return (
        <span
          role="status"
          aria-label="Outcome: APPROVE"
          className={`inline-flex items-center font-semibold rounded-sm border uppercase tracking-wider transition-colors duration-150 select-none ${sizeClasses} ${className}`}
          style={{
            borderColor: 'var(--approve)',
            color: 'var(--approve)',
            backgroundColor: 'var(--approve-bg)',
          }}
        >
          <Check className="w-3 h-3 stroke-[2.5]" aria-hidden="true" />
          <span>APPROVE</span>
        </span>
      );

    case 'REVIEW':
      return (
        <span
          role="status"
          aria-label="Outcome: REVIEW"
          className={`inline-flex items-center font-semibold rounded-sm border uppercase tracking-wider transition-colors duration-150 select-none ${sizeClasses} ${className}`}
          style={{
            borderColor: 'var(--review)',
            color: 'var(--review)',
            backgroundColor: 'var(--review-bg)',
          }}
        >
          <Flag className="w-3 h-3 fill-current stroke-[1.5]" aria-hidden="true" />
          <span>REVIEW</span>
        </span>
      );

    case 'DECLINE':
      return (
        <span
          role="status"
          aria-label="Outcome: DECLINE"
          className={`inline-flex items-center font-semibold rounded-sm border uppercase tracking-wider transition-colors duration-150 select-none ${sizeClasses} ${className}`}
          style={{
            borderColor: 'var(--decline)',
            color: 'var(--decline-text)',
            backgroundColor: 'var(--decline-bg)',
          }}
        >
          <X className="w-3 h-3 stroke-[2.5]" aria-hidden="true" />
          <span>DECLINE</span>
        </span>
      );

    default:
      return (
        <span
          role="status"
          aria-label={`Outcome: ${normalized}`}
          className={`inline-flex items-center font-semibold rounded-sm border uppercase tracking-wider select-none border-border text-text-muted bg-surface-raised ${sizeClasses} ${className}`}
        >
          <span>{normalized}</span>
        </span>
      );
  }
};
