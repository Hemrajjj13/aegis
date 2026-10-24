import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DecisionBadge } from '../components/DecisionBadge';

describe('DecisionBadge Component', () => {
  it('renders APPROVE badge with geometric icon, text label, and aria-label', () => {
    render(<DecisionBadge outcome="APPROVE" />);
    
    const badge = screen.getByRole('status', { name: 'Outcome: APPROVE' });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent('APPROVE');
    
    // Invariant: Non-color reliance - icon must be present
    const icon = badge.querySelector('svg');
    expect(icon).toBeInTheDocument();
  });

  it('renders REVIEW badge with geometric flag icon, text label, and aria-label', () => {
    render(<DecisionBadge outcome="REVIEW" />);
    
    const badge = screen.getByRole('status', { name: 'Outcome: REVIEW' });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent('REVIEW');
    
    const icon = badge.querySelector('svg');
    expect(icon).toBeInTheDocument();
  });

  it('renders DECLINE badge with geometric X icon, text label, and aria-label', () => {
    render(<DecisionBadge outcome="DECLINE" />);
    
    const badge = screen.getByRole('status', { name: 'Outcome: DECLINE' });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent('DECLINE');
    
    const icon = badge.querySelector('svg');
    expect(icon).toBeInTheDocument();
  });

  it('handles lowercase or mixed-case outcome inputs reliably', () => {
    render(<DecisionBadge outcome="approve" />);
    const badge = screen.getByRole('status', { name: 'Outcome: APPROVE' });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent('APPROVE');
  });

  it('renders fallback for unexpected outcomes with proper status role', () => {
    render(<DecisionBadge outcome="UNKNOWN_OUTCOME" />);
    const badge = screen.getByRole('status', { name: 'Outcome: UNKNOWN_OUTCOME' });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent('UNKNOWN_OUTCOME');
  });
});
