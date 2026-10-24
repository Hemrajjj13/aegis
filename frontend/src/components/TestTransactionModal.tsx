import React, { useState, useEffect } from 'react';
import { X, Send, AlertTriangle, CheckCircle2, RefreshCw } from 'lucide-react';
import { apiClient, DEV_TENANT_ID } from '../api/client';
import type { TransactionEvent, ErrorResponse, DecisionResponse } from '../types/decision';

interface TestTransactionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess?: (decision: DecisionResponse) => void;
}

export const TestTransactionModal: React.FC<TestTransactionModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [eventId, setEventId] = useState<string>(() => crypto.randomUUID());
  const [eventTime, setEventTime] = useState<string>(() => new Date().toISOString());
  const [amountRupees, setAmountRupees] = useState<string>('1250.00');
  const [currency, setCurrency] = useState<string>('INR');
  const [paymentMethod, setPaymentMethod] = useState<TransactionEvent['payment_method']>('UPI');
  const [channel, setChannel] = useState<NonNullable<TransactionEvent['channel']>>('WEB');
  const [merchantId, setMerchantId] = useState<string>('mer_acme_ecom_01');
  const [merchantName, setMerchantName] = useState<string>('Acme Electronics');
  const [cardToken, setCardToken] = useState<string>('');

  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [errorResponse, setErrorResponse] = useState<ErrorResponse | null>(null);
  const [lastSuccess, setLastSuccess] = useState<DecisionResponse | null>(null);

  // Esc key closes modal
  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) {
      window.addEventListener('keydown', handleEsc);
    }
    return () => window.removeEventListener('keydown', handleEsc);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const handleRegenerateId = () => {
    setEventId(crypto.randomUUID());
    setEventTime(new Date().toISOString());
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setErrorResponse(null);
    setLastSuccess(null);

    const minorUnits = Math.round(parseFloat(amountRupees || '0') * 100);

    const payload: TransactionEvent = {
      event_id: eventId,
      event_time: eventTime,
      sequence_number: 1,
      tenant_id: DEV_TENANT_ID,
      amount_minor_units: minorUnits,
      currency,
      payment_method: paymentMethod,
      channel,
      merchant_id: merchantId,
      merchant_name: merchantName,
      ...(cardToken.trim() ? { card_token: cardToken.trim() } : {}),
    };

    try {
      const response = await apiClient.POST('/decisions', {
        body: payload,
      });

      if (response.data) {
        setLastSuccess(response.data);
        if (onSuccess) {
          onSuccess(response.data);
        }
        // Auto-refresh ID for next submission
        setEventId(crypto.randomUUID());
        setEventTime(new Date().toISOString());
      } else if (response.error) {
        // RFC 7807 Error Response
        setErrorResponse(response.error as ErrorResponse);
      }
    } catch (err: unknown) {
      console.error('[TestTransactionModal] Network/Client exception:', err);
      setErrorResponse({
        type: 'about:blank',
        title: 'Network Communication Error',
        status: 500,
        detail: err instanceof Error ? err.message : String(err),
      });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="test-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm overflow-y-auto"
    >
      <div className="relative w-full max-w-xl bg-surface border border-border rounded-lg shadow-2xl p-6 text-text my-8">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-border">
          <div>
            <h2 id="test-modal-title" className="text-base font-semibold text-text">
              Send Test Transaction
            </h2>
            <p className="text-xs text-text-muted mt-0.5">
              POST /v1/decisions (Dev-Only Harness per OpenAPI 3.1 Spec)
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-sm text-text-muted hover:text-text hover:bg-surface-raised transition-colors focus-visible:ring-2 focus-visible:ring-accent"
            aria-label="Close modal"
          >
            <X className="w-5 h-5" aria-hidden="true" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="mt-4 space-y-3.5 text-xs">
          {/* Event ID with regenerate */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="input-event-id" className="font-medium text-text">
                Event ID (UUID)
              </label>
              <button
                type="button"
                onClick={handleRegenerateId}
                className="inline-flex items-center gap-1 text-[11px] text-accent hover:underline focus-visible:outline-none"
              >
                <RefreshCw className="w-3 h-3" aria-hidden="true" />
                <span>New UUID</span>
              </button>
            </div>
            <input
              id="input-event-id"
              type="text"
              required
              value={eventId}
              onChange={(e) => setEventId(e.target.value)}
              className="w-full px-2.5 py-1.5 rounded-sm bg-surface-raised border border-border font-mono text-xs focus-visible:ring-2 focus-visible:ring-accent"
            />
          </div>

          {/* Amount & Currency */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="input-amount" className="block font-medium text-text mb-1">
                Amount (Major Units / ₹)
              </label>
              <input
                id="input-amount"
                type="number"
                step="0.01"
                min="0.01"
                required
                value={amountRupees}
                onChange={(e) => setAmountRupees(e.target.value)}
                className="w-full px-2.5 py-1.5 rounded-sm bg-surface-raised border border-border font-mono text-xs focus-visible:ring-2 focus-visible:ring-accent"
              />
            </div>

            <div>
              <label htmlFor="input-currency" className="block font-medium text-text mb-1">
                Currency
              </label>
              <input
                id="input-currency"
                type="text"
                required
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
                className="w-full px-2.5 py-1.5 rounded-sm bg-surface-raised border border-border font-mono text-xs focus-visible:ring-2 focus-visible:ring-accent"
              />
            </div>
          </div>

          {/* Payment Method & Channel */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="input-payment-method" className="block font-medium text-text mb-1">
                Payment Method
              </label>
              <select
                id="input-payment-method"
                value={paymentMethod}
                onChange={(e) => setPaymentMethod(e.target.value as TransactionEvent['payment_method'])}
                className="w-full px-2.5 py-1.5 rounded-sm bg-surface-raised border border-border text-xs focus-visible:ring-2 focus-visible:ring-accent"
              >
                <option value="UPI">UPI</option>
                <option value="CARD">CARD</option>
                <option value="NETBANKING">NETBANKING</option>
                <option value="WALLET">WALLET</option>
                <option value="BNPL">BNPL</option>
                <option value="EMI">EMI</option>
                <option value="NACH">NACH</option>
              </select>
            </div>

            <div>
              <label htmlFor="input-channel" className="block font-medium text-text mb-1">
                Channel
              </label>
              <select
                id="input-channel"
                value={channel}
                onChange={(e) => setChannel(e.target.value as NonNullable<TransactionEvent['channel']>)}
                className="w-full px-2.5 py-1.5 rounded-sm bg-surface-raised border border-border text-xs focus-visible:ring-2 focus-visible:ring-accent"
              >
                <option value="WEB">WEB</option>
                <option value="MOBILE_APP">MOBILE_APP</option>
                <option value="POS">POS</option>
                <option value="API">API</option>
              </select>
            </div>
          </div>

          {/* Merchant ID & Name */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="input-merchant-id" className="block font-medium text-text mb-1">
                Merchant ID
              </label>
              <input
                id="input-merchant-id"
                type="text"
                value={merchantId}
                onChange={(e) => setMerchantId(e.target.value)}
                className="w-full px-2.5 py-1.5 rounded-sm bg-surface-raised border border-border font-mono text-xs focus-visible:ring-2 focus-visible:ring-accent"
              />
            </div>

            <div>
              <label htmlFor="input-merchant-name" className="block font-medium text-text mb-1">
                Merchant Name
              </label>
              <input
                id="input-merchant-name"
                type="text"
                value={merchantName}
                onChange={(e) => setMerchantName(e.target.value)}
                className="w-full px-2.5 py-1.5 rounded-sm bg-surface-raised border border-border text-xs focus-visible:ring-2 focus-visible:ring-accent"
              />
            </div>
          </div>

          {/* Optional Card Token (Zero PAN rule: only network token tok_...) */}
          <div>
            <label htmlFor="input-card-token" className="block font-medium text-text mb-1">
              Card Token (Optional, e.g. <code className="text-text-muted">tok_...</code> — Zero PAN invariant)
            </label>
            <input
              id="input-card-token"
              type="text"
              placeholder="tok_1234567890123456"
              value={cardToken}
              onChange={(e) => setCardToken(e.target.value)}
              className="w-full px-2.5 py-1.5 rounded-sm bg-surface-raised border border-border font-mono text-xs focus-visible:ring-2 focus-visible:ring-accent"
            />
            <p className="text-[11px] text-text-muted mt-0.5">
              Strict Zero PAN rule: Raw PANs are rejected by backend Luhn validation.
            </p>
          </div>

          {/* RFC 7807 Error Presentation */}
          {errorResponse && (
            <div
              role="alert"
              className="p-3 rounded-md bg-red-500/10 border border-red-500/30 text-red-400 space-y-1.5"
            >
              <div className="flex items-center gap-1.5 font-semibold text-xs text-red-300">
                <AlertTriangle className="w-4 h-4 shrink-0" aria-hidden="true" />
                <span>
                  RFC 7807 Error: {errorResponse.status} {errorResponse.title}
                </span>
              </div>
              <p className="text-xs text-red-200">{errorResponse.detail}</p>
              {errorResponse.invalid_params && errorResponse.invalid_params.length > 0 && (
                <div className="mt-2 pt-2 border-t border-red-500/20 text-[11px]">
                  <span className="font-semibold uppercase tracking-wider">Invalid Parameters:</span>
                  <ul className="mt-1 space-y-1 font-mono">
                    {errorResponse.invalid_params.map((param, i) => (
                      <li key={i} className="flex gap-2">
                        <strong className="text-white">{param.name}:</strong>
                        <span>{param.reason}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}

          {/* Success Banner */}
          {lastSuccess && (
            <div
              role="status"
              className="p-3 rounded-md bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 flex items-start gap-2"
            >
              <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0 text-emerald-400" aria-hidden="true" />
              <div className="text-xs space-y-0.5">
                <div className="font-semibold text-emerald-200">
                  Decision Evaluated: <span className="uppercase">{lastSuccess.outcome}</span> (Score: {lastSuccess.score.toFixed(1)})
                </div>
                <div className="font-mono text-[11px] text-emerald-300/80">
                  ID: {lastSuccess.decision_id} | Latency: {lastSuccess.latency_ms.toFixed(1)} ms
                </div>
              </div>
            </div>
          )}

          {/* Actions */}
          <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 rounded-sm border border-border bg-surface hover:bg-surface-raised text-text font-medium transition-colors"
            >
              Close
            </button>
            <button
              type="submit"
              disabled={isLoading}
              className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-sm bg-accent-solid hover:bg-accent-hover text-white font-semibold transition-colors disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-accent"
            >
              {isLoading ? (
                <span>Submitting...</span>
              ) : (
                <>
                  <Send className="w-3.5 h-3.5" aria-hidden="true" />
                  <span>POST Transaction</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
