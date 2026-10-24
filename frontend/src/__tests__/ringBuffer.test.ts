import { describe, it, expect } from 'vitest';
import { pushToRingBuffer, RING_BUFFER_CAPACITY } from '../state/decisionReducer';

describe('pushToRingBuffer utility', () => {
  it('appends items without dropping when below capacity', () => {
    const current = [2, 1];
    const incoming = [3];
    const result = pushToRingBuffer(current, incoming, 10);
    expect(result).toEqual([3, 2, 1]);
    expect(result.length).toBe(3);
  });

  it('strictly caps total items at maximum capacity (2,000 items)', () => {
    const current: number[] = Array.from({ length: 1995 }, (_, i) => i);
    const incoming: number[] = [10001, 10002, 10003, 10004, 10005, 10006, 10007, 10008, 10009, 10010]; // 10 new items

    const result = pushToRingBuffer(current, incoming, RING_BUFFER_CAPACITY);
    expect(result.length).toBe(RING_BUFFER_CAPACITY);
    expect(result[0]).toBe(10001);
    expect(result[9]).toBe(10010);
    // Oldest items at the end should have rolled off
  });

  it('discards oldest items when inserting massive batch exceeding capacity', () => {
    const massiveBatch = Array.from({ length: 3000 }, (_, i) => `item_${i}`);
    const result = pushToRingBuffer([], massiveBatch, RING_BUFFER_CAPACITY);

    expect(result.length).toBe(RING_BUFFER_CAPACITY);
    expect(result[0]).toBe('item_0');
    expect(result[RING_BUFFER_CAPACITY - 1]).toBe(`item_${RING_BUFFER_CAPACITY - 1}`);
  });
});
