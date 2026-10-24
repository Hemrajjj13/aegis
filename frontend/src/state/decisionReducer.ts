import type { DecisionResponse } from '../types/decision';

export const RING_BUFFER_CAPACITY = 2000;

export interface DecisionState {
  decisions: DecisionResponse[];
  decisionIds: Set<string>;
  isPaused: boolean;
  pausedQueue: DecisionResponse[];
  selectedDecisionId: string | null;
}

export type DecisionAction =
  | { type: 'ADD_DECISION'; payload: DecisionResponse }
  | { type: 'ADD_DECISIONS_BATCH'; payload: DecisionResponse[] }
  | { type: 'TOGGLE_PAUSE' }
  | { type: 'SET_PAUSE'; payload: boolean }
  | { type: 'RESUME_AND_FLUSH' }
  | { type: 'SELECT_DECISION'; payload: string | null }
  | { type: 'SELECT_NEXT' }
  | { type: 'SELECT_PREV' }
  | { type: 'CLEAR_ALL' };

export function createInitialState(isPaused = false): DecisionState {
  return {
    decisions: [],
    decisionIds: new Set<string>(),
    isPaused,
    pausedQueue: [],
    selectedDecisionId: null,
  };
}

/**
 * Pure ring buffer inserter maintaining a hard bound of maxCapacity items.
 * Removes oldest items when capacity is exceeded.
 */
export function pushToRingBuffer<T>(
  currentItems: readonly T[],
  newItems: readonly T[],
  maxCapacity: number = RING_BUFFER_CAPACITY
): T[] {
  const combined = [...newItems, ...currentItems];
  if (combined.length <= maxCapacity) {
    return combined;
  }
  return combined.slice(0, maxCapacity);
}

export function decisionReducer(state: DecisionState, action: DecisionAction): DecisionState {
  switch (action.type) {
    case 'ADD_DECISION': {
      const decision = action.payload;
      // Deduplicate by decision_id
      if (state.decisionIds.has(decision.decision_id)) {
        return state;
      }

      const nextIds = new Set(state.decisionIds);
      nextIds.add(decision.decision_id);

      if (state.isPaused) {
        // When stream is paused, accumulate incoming decisions up to the cap
        const nextQueue = pushToRingBuffer(state.pausedQueue, [decision], RING_BUFFER_CAPACITY);
        return {
          ...state,
          pausedQueue: nextQueue,
          decisionIds: nextIds,
        };
      }

      // Normal real-time streaming: push to active ring buffer
      const nextDecisions = pushToRingBuffer(state.decisions, [decision], RING_BUFFER_CAPACITY);

      // Clean up evicted IDs if ring buffer rolled over
      if (state.decisions.length >= RING_BUFFER_CAPACITY && nextDecisions.length === RING_BUFFER_CAPACITY) {
        const activeIdSet = new Set(nextDecisions.map((d) => d.decision_id));
        return {
          ...state,
          decisions: nextDecisions,
          decisionIds: activeIdSet,
        };
      }

      return {
        ...state,
        decisions: nextDecisions,
        decisionIds: nextIds,
      };
    }

    case 'ADD_DECISIONS_BATCH': {
      const freshItems = action.payload.filter((d) => !state.decisionIds.has(d.decision_id));
      if (freshItems.length === 0) {
        return state;
      }

      const nextIds = new Set(state.decisionIds);
      freshItems.forEach((d) => nextIds.add(d.decision_id));

      if (state.isPaused) {
        const nextQueue = pushToRingBuffer(state.pausedQueue, freshItems, RING_BUFFER_CAPACITY);
        return {
          ...state,
          pausedQueue: nextQueue,
          decisionIds: nextIds,
        };
      }

      const nextDecisions = pushToRingBuffer(state.decisions, freshItems, RING_BUFFER_CAPACITY);
      const activeIdSet = new Set(nextDecisions.map((d) => d.decision_id));

      return {
        ...state,
        decisions: nextDecisions,
        decisionIds: activeIdSet,
      };
    }

    case 'TOGGLE_PAUSE': {
      if (state.isPaused) {
        // Unpausing: flush accumulated paused decisions into active buffer
        return decisionReducer(state, { type: 'RESUME_AND_FLUSH' });
      }
      return {
        ...state,
        isPaused: true,
      };
    }

    case 'SET_PAUSE': {
      if (!action.payload && state.isPaused) {
        return decisionReducer(state, { type: 'RESUME_AND_FLUSH' });
      }
      return {
        ...state,
        isPaused: action.payload,
      };
    }

    case 'RESUME_AND_FLUSH': {
      if (state.pausedQueue.length === 0) {
        return {
          ...state,
          isPaused: false,
        };
      }

      // Merge accumulated paused decisions into active decisions
      const merged = pushToRingBuffer(state.decisions, state.pausedQueue, RING_BUFFER_CAPACITY);
      const activeIds = new Set(merged.map((d) => d.decision_id));

      return {
        ...state,
        isPaused: false,
        decisions: merged,
        pausedQueue: [],
        decisionIds: activeIds,
      };
    }

    case 'SELECT_DECISION': {
      return {
        ...state,
        selectedDecisionId: action.payload,
      };
    }

    case 'SELECT_NEXT': {
      if (state.decisions.length === 0) return state;
      if (!state.selectedDecisionId) {
        return {
          ...state,
          selectedDecisionId: state.decisions[0].decision_id,
        };
      }
      const currentIndex = state.decisions.findIndex((d) => d.decision_id === state.selectedDecisionId);
      if (currentIndex === -1) {
        return { ...state, selectedDecisionId: state.decisions[0].decision_id };
      }
      const nextIndex = Math.min(currentIndex + 1, state.decisions.length - 1);
      return {
        ...state,
        selectedDecisionId: state.decisions[nextIndex].decision_id,
      };
    }

    case 'SELECT_PREV': {
      if (state.decisions.length === 0) return state;
      if (!state.selectedDecisionId) {
        return {
          ...state,
          selectedDecisionId: state.decisions[0].decision_id,
        };
      }
      const currentIndex = state.decisions.findIndex((d) => d.decision_id === state.selectedDecisionId);
      if (currentIndex === -1) {
        return { ...state, selectedDecisionId: state.decisions[0].decision_id };
      }
      const prevIndex = Math.max(currentIndex - 1, 0);
      return {
        ...state,
        selectedDecisionId: state.decisions[prevIndex].decision_id,
      };
    }

    case 'CLEAR_ALL': {
      return createInitialState(state.isPaused);
    }

    default:
      return state;
  }
}
