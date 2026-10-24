import { describe, it, expect } from 'vitest';
import {
  decisionReducer,
  createInitialState,
  RING_BUFFER_CAPACITY,
  type DecisionState,
} from '../state/decisionReducer';
import type { DecisionResponse } from '../types/decision';

function createMockDecision(id: string, outcome: 'APPROVE' | 'DECLINE' | 'REVIEW' = 'APPROVE'): DecisionResponse {
  return {
    decision_id: id,
    event_id: `evt_${id}`,
    outcome,
    score: 15.0,
    hard_rule_fired: false,
    ruleset_id: 'rs_default',
    ruleset_version_hash: 'hash_abc123',
    rules_fired: [],
    degraded: false,
    latency_ms: 12.5,
    timestamp: new Date().toISOString(),
  };
}

describe('decisionReducer', () => {
  it('initializes with clean default state', () => {
    const state = createInitialState();
    expect(state.decisions).toEqual([]);
    expect(state.isPaused).toBe(false);
    expect(state.pausedQueue).toEqual([]);
    expect(state.selectedDecisionId).toBeNull();
  });

  it('adds incoming decision to active ring buffer and tracks decision_id', () => {
    const state = createInitialState();
    const d1 = createMockDecision('dec_001');

    const nextState = decisionReducer(state, { type: 'ADD_DECISION', payload: d1 });
    expect(nextState.decisions.length).toBe(1);
    expect(nextState.decisions[0].decision_id).toBe('dec_001');
    expect(nextState.decisionIds.has('dec_001')).toBe(true);
  });

  it('de-duplicates incoming decisions by decision_id', () => {
    const state = createInitialState();
    const d1 = createMockDecision('dec_dup_001');

    const state1 = decisionReducer(state, { type: 'ADD_DECISION', payload: d1 });
    expect(state1.decisions.length).toBe(1);

    // Attempting to add duplicate decision with same decision_id
    const state2 = decisionReducer(state1, { type: 'ADD_DECISION', payload: d1 });
    expect(state2.decisions.length).toBe(1);
    expect(state2).toBe(state1); // Preserves exact state reference when deduplicated
  });

  it('accumulates incoming decisions in pausedQueue when isPaused is true', () => {
    let state = createInitialState();
    state = decisionReducer(state, { type: 'TOGGLE_PAUSE' });
    expect(state.isPaused).toBe(true);

    const d1 = createMockDecision('dec_paused_1');
    const d2 = createMockDecision('dec_paused_2');

    state = decisionReducer(state, { type: 'ADD_DECISION', payload: d1 });
    state = decisionReducer(state, { type: 'ADD_DECISION', payload: d2 });

    // Invariant: active decisions must remain empty while paused
    expect(state.decisions.length).toBe(0);
    // Paused queue accumulates the 2 incoming events
    expect(state.pausedQueue.length).toBe(2);
    expect(state.pausedQueue[0].decision_id).toBe('dec_paused_2');
    expect(state.pausedQueue[1].decision_id).toBe('dec_paused_1');
  });

  it('flushes pausedQueue into active buffer when resuming stream', () => {
    let state = createInitialState();
    const initialDec = createMockDecision('dec_live_0');
    state = decisionReducer(state, { type: 'ADD_DECISION', payload: initialDec });

    // Pause
    state = decisionReducer(state, { type: 'SET_PAUSE', payload: true });
    state = decisionReducer(state, { type: 'ADD_DECISION', payload: createMockDecision('dec_paused_1') });
    state = decisionReducer(state, { type: 'ADD_DECISION', payload: createMockDecision('dec_paused_2') });

    expect(state.decisions.length).toBe(1);
    expect(state.pausedQueue.length).toBe(2);

    // Unpause / Resume
    state = decisionReducer(state, { type: 'RESUME_AND_FLUSH' });

    expect(state.isPaused).toBe(false);
    expect(state.pausedQueue.length).toBe(0);
    expect(state.decisions.length).toBe(3);
    // Newly flushed items prepended at top
    expect(state.decisions[0].decision_id).toBe('dec_paused_2');
    expect(state.decisions[1].decision_id).toBe('dec_paused_1');
    expect(state.decisions[2].decision_id).toBe('dec_live_0');
  });

  it('strictly bounds decisions ring buffer at 2,000 events', () => {
    let state = createInitialState();
    const batch = Array.from({ length: 2050 }, (_, i) => createMockDecision(`dec_${i}`));

    state = decisionReducer(state, { type: 'ADD_DECISIONS_BATCH', payload: batch });

    expect(state.decisions.length).toBe(RING_BUFFER_CAPACITY);
    expect(state.decisions[0].decision_id).toBe('dec_0');
  });

  it('navigates row selection with SELECT_NEXT (j) and SELECT_PREV (k)', () => {
    let state: DecisionState = createInitialState();
    state = decisionReducer(state, { type: 'ADD_DECISION', payload: createMockDecision('dec_1') });
    state = decisionReducer(state, { type: 'ADD_DECISION', payload: createMockDecision('dec_2') });
    state = decisionReducer(state, { type: 'ADD_DECISION', payload: createMockDecision('dec_3') });
    // decisions are [dec_3, dec_2, dec_1]

    // Initially no selection
    expect(state.selectedDecisionId).toBeNull();

    // SELECT_NEXT selects first row
    state = decisionReducer(state, { type: 'SELECT_NEXT' });
    expect(state.selectedDecisionId).toBe('dec_3');

    // SELECT_NEXT moves down to dec_2
    state = decisionReducer(state, { type: 'SELECT_NEXT' });
    expect(state.selectedDecisionId).toBe('dec_2');

    // SELECT_NEXT moves down to dec_1
    state = decisionReducer(state, { type: 'SELECT_NEXT' });
    expect(state.selectedDecisionId).toBe('dec_1');

    // SELECT_NEXT at bottom remains clamped to dec_1
    state = decisionReducer(state, { type: 'SELECT_NEXT' });
    expect(state.selectedDecisionId).toBe('dec_1');

    // SELECT_PREV moves up to dec_2
    state = decisionReducer(state, { type: 'SELECT_PREV' });
    expect(state.selectedDecisionId).toBe('dec_2');

    // SELECT_PREV moves up to dec_3
    state = decisionReducer(state, { type: 'SELECT_PREV' });
    expect(state.selectedDecisionId).toBe('dec_3');

    // SELECT_PREV at top remains clamped to dec_3
    state = decisionReducer(state, { type: 'SELECT_PREV' });
    expect(state.selectedDecisionId).toBe('dec_3');
  });
});
