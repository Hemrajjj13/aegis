/**
 * Timestamp and numeric formatters for Project Aegis Console.
 * Conforms to Doc 10 §2.2 (JetBrains Mono tabular figures for metrics, amounts, and latencies).
 */

/**
 * Formats ISO timestamp to IST (UTC+5:30) with millisecond precision.
 */
export function formatTimeIST(isoString: string): string {
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return isoString || '—';

    // Format strictly in Asia/Kolkata timezone per Doc 18 §1.1
    const formatter = new Intl.DateTimeFormat('en-IN', {
      timeZone: 'Asia/Kolkata',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    });

    const ms = String(date.getUTCMilliseconds()).padStart(3, '0');
    return `${formatter.format(date)}.${ms}`;
  } catch {
    return isoString;
  }
}

/**
 * Formats latency in milliseconds with tabular alignment.
 */
export function formatLatency(latencyMs: number | undefined): string {
  if (typeof latencyMs !== 'number' || isNaN(latencyMs)) return '— ms';
  return `${latencyMs.toFixed(1)} ms`;
}

/**
 * Formats risk score (0.0 to 100.0).
 */
export function formatScore(score: number | undefined): string {
  if (typeof score !== 'number' || isNaN(score)) return '—';
  return score.toFixed(1);
}

/**
 * Formats minor units (e.g. paise / cents) to currency string.
 */
export function formatAmount(minorUnits: number, currency = 'INR'): string {
  const major = minorUnits / 100;
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
    }).format(major);
  } catch {
    return `${currency} ${major.toFixed(2)}`;
  }
}
