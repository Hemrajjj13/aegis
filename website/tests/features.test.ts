import { describe, it, expect } from 'vitest';
import { features } from '../src/data/features';

describe('features statuses', () => {
  it('only allows In development or Planned', () => {
    for (const f of features) {
      expect(['In development', 'Planned']).toContain(f.status);
    }
  });
  it('marks nothing as Available', () => {
    for (const f of features) {
      expect(f.status).not.toBe('Available');
    }
  });
});
