import { useEffect, useRef, useState, useCallback } from 'react';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { getAccessToken } from '../api/authTokens';
import type { DecisionAction } from '../state/decisionReducer';
import type { DecisionResponse, StreamConnectionStatus } from '../types/decision';

interface UseDecisionStreamOptions {
  dispatch: React.Dispatch<DecisionAction>;
  url?: string;
  tenantId?: string;
}

/**
 * SSE hook using @microsoft/fetch-event-source (NOT native EventSource).
 * 
 * Implements:
 * 1. Bearer JWT authorization header transmission from memory.
 * 2. Optional X-Tenant-Id fallback header for development.
 * 3. Ignores the "connected" handshake event.
 * 4. Exponential backoff reconnection with visible status indicator.
 * 5. Clean stream teardown via AbortController.
 */
export function useDecisionStream({
  dispatch,
  url = '/v1/events/stream',
  tenantId,
}: UseDecisionStreamOptions) {
  const [status, setStatus] = useState<StreamConnectionStatus>('connecting');
  const abortControllerRef = useRef<AbortController | null>(null);
  const reconnectAttemptRef = useRef<number>(0);
  const isMountedRef = useRef<boolean>(true);

  const connect = useCallback(() => {
    if (!isMountedRef.current) return;

    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    const controller = new AbortController();
    abortControllerRef.current = controller;

    setStatus(reconnectAttemptRef.current > 0 ? 'reconnecting' : 'connecting');

    const headers: Record<string, string> = {
      'Accept': 'text/event-stream',
    };
    const token = getAccessToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    if (tenantId) {
      headers['X-Tenant-Id'] = tenantId;
    }

    fetchEventSource(url, {
      method: 'GET',
      headers,
      signal: controller.signal,

      async onopen(response) {
        if (!isMountedRef.current) return;

        if (response.ok && response.headers.get('content-type')?.includes('text/event-stream')) {
          reconnectAttemptRef.current = 0;
          setStatus('connected');
        } else {
          throw new Error(`SSE handshake failed with status ${response.status}`);
        }
      },

      onmessage(event) {
        if (!isMountedRef.current) return;

        // Invariant per task spec: Ignore the initial "connected" handshake event
        if (event.event === 'connected') {
          return;
        }

        if (event.event === 'decision' || !event.event) {
          try {
            const decision: DecisionResponse = JSON.parse(event.data);
            if (decision && decision.decision_id) {
              dispatch({ type: 'ADD_DECISION', payload: decision });
            }
          } catch (err) {
            console.error('[useDecisionStream] Failed to parse SSE decision event JSON:', err);
          }
        }
      },

      onclose() {
        if (!isMountedRef.current) return;
        setStatus('disconnected');
        // Let onerror or automatic retry cycle handle reconnect
      },

      onerror(err) {
        if (!isMountedRef.current || controller.signal.aborted) {
          return; // Do not reconnect if unmounted or aborted intentionally
        }

        reconnectAttemptRef.current += 1;
        setStatus('reconnecting');

        // Exponential backoff with jitter: min(1000 * 1.5^attempt, 30000) ± 500ms
        const baseDelay = Math.min(1000 * Math.pow(1.5, reconnectAttemptRef.current), 30000);
        const jitter = Math.random() * 500;
        const retryDelay = baseDelay + jitter;

        console.warn(
          `[useDecisionStream] SSE connection error. Reconnecting (attempt #${reconnectAttemptRef.current}) in ${Math.round(retryDelay)}ms...`,
          err
        );

        // Returning the retry delay instructs fetchEventSource to wait before reconnecting
        return retryDelay;
      },
    }).catch((err) => {
      if (controller.signal.aborted) return;
      if (isMountedRef.current) {
        setStatus('disconnected');
        console.error('[useDecisionStream] Fatal SSE error:', err);
      }
    });
  }, [dispatch, tenantId, url]);

  useEffect(() => {
    isMountedRef.current = true;
    connect();

    return () => {
      isMountedRef.current = false;
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [connect]);

  const reconnect = useCallback(() => {
    reconnectAttemptRef.current = 0;
    connect();
  }, [connect]);

  return {
    status,
    reconnect,
  };
}
