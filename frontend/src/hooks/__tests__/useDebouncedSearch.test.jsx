// TICKET-ADV117 — Unit tests for useDebouncedSearch custom hook
import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useDebouncedSearch } from '../useDebouncedSearch.js';

describe('useDebouncedSearch hook (TICKET-ADV117)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns initial query value immediately on mount', () => {
    const { result } = renderHook(() => useDebouncedSearch('initial', 300));
    expect(result.current).toBe('initial');
  });

  it('debounces rapid updates and only updates value after delay ms of inactivity', () => {
    const { result, rerender } = renderHook(
      ({ query, delay }) => useDebouncedSearch(query, delay),
      { initialProps: { query: 'A', delay: 300 } }
    );

    expect(result.current).toBe('A');

    // Keystroke 1 at 100ms
    vi.advanceTimersByTime(100);
    rerender({ query: 'AB', delay: 300 });
    expect(result.current).toBe('A');

    // Keystroke 2 at 200ms
    vi.advanceTimersByTime(100);
    rerender({ query: 'ABC', delay: 300 });
    expect(result.current).toBe('A');

    // Advance 299ms (total 499ms, but only 299ms since last keystroke)
    act(() => {
      vi.advanceTimersByTime(299);
    });
    expect(result.current).toBe('A');

    // Advance 1ms more (reaching 300ms since last keystroke 'ABC')
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(result.current).toBe('ABC');
  });

  it('handles dynamic delay changes at runtime', () => {
    const { result, rerender } = renderHook(
      ({ query, delay }) => useDebouncedSearch(query, delay),
      { initialProps: { query: 'test', delay: 500 } }
    );

    // Update query with 500ms delay
    rerender({ query: 'test2', delay: 500 });
    vi.advanceTimersByTime(300);
    expect(result.current).toBe('test');

    // Change delay to 200ms dynamically
    rerender({ query: 'test2', delay: 200 });

    act(() => {
      vi.advanceTimersByTime(200);
    });
    expect(result.current).toBe('test2');
  });

  it('cleans up pending timer on unmount', () => {
    const clearTimeoutSpy = vi.spyOn(global, 'clearTimeout');
    const { rerender, unmount } = renderHook(
      ({ query, delay }) => useDebouncedSearch(query, delay),
      { initialProps: { query: 'first', delay: 300 } }
    );

    rerender({ query: 'second', delay: 300 });

    unmount();

    expect(clearTimeoutSpy).toHaveBeenCalled();
  });
});
