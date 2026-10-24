import type { components } from '../api/schema';

// Pure type alias re-exports from generated OpenAPI contract.
// NO hand-written API types permitted per AGENTS.md and task requirements.
export type DecisionResponse = components['schemas']['DecisionResponse'];
export type TransactionEvent = components['schemas']['TransactionEvent'];
export type ErrorResponse = components['schemas']['ErrorResponse'];
export type FiredRuleSummary = components['schemas']['FiredRuleSummary'];
export type DecisionOutcome = DecisionResponse['outcome']; // 'APPROVE' | 'DECLINE' | 'REVIEW'

export type StreamConnectionStatus = 'connected' | 'connecting' | 'reconnecting' | 'disconnected';
