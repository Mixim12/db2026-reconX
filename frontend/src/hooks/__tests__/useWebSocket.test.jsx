// TICKET-ADV115 — Unit tests for useWebSocket custom hook
import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useWebSocket } from '../useWebSocket.js';

class MockWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;
  static instances = [];

  constructor(url) {
    this.url = url;
    this.readyState = MockWebSocket.CONNECTING;
    this.onopen = null;
    this.onmessage = null;
    this.onerror = null;
    this.onclose = null;
    this.sentMessages = [];
    MockWebSocket.instances.push(this);
  }

  send(data) {
    if (this.readyState === MockWebSocket.OPEN) {
      this.sentMessages.push(data);
    } else {
      throw new Error('WebSocket is not open');
    }
  }

  close() {
    this.readyState = MockWebSocket.CLOSED;
    if (this.onclose) {
      this.onclose({ wasClean: true });
    }
  }

  // Helper test methods
  triggerOpen() {
    this.readyState = MockWebSocket.OPEN;
    if (this.onopen) {
      this.onopen({ type: 'open' });
    }
  }

  triggerMessage(data) {
    if (this.onmessage) {
      this.onmessage({ data: typeof data === 'string' ? data : JSON.stringify(data) });
    }
  }

  triggerClose() {
    this.readyState = MockWebSocket.CLOSED;
    if (this.onclose) {
      this.onclose({ wasClean: false, code: 1006 });
    }
  }

  triggerError(err) {
    if (this.onerror) {
      this.onerror(err);
    }
  }
}

describe('useWebSocket hook (TICKET-ADV115)', () => {
  const originalWebSocket = global.WebSocket;

  beforeEach(() => {
    vi.useFakeTimers();
    MockWebSocket.instances = [];
    global.WebSocket = MockWebSocket;
  });

  afterEach(() => {
    global.WebSocket = originalWebSocket;
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('opens exactly ONE WebSocket on mount and closes it on unmount', () => {
    const url = 'ws://localhost:8080/ws';
    const { result, unmount } = renderHook(() => useWebSocket(url));

    expect(MockWebSocket.instances.length).toBe(1);
    expect(result.current.status).toBe('CONNECTING');

    const ws = MockWebSocket.instances[0];

    act(() => {
      ws.triggerOpen();
    });

    expect(result.current.status).toBe('OPEN');

    unmount();

    expect(ws.readyState).toBe(MockWebSocket.CLOSED);
  });

  it('updates data on parsed JSON and raw string messages', () => {
    const url = 'ws://localhost:8080/ws';
    const { result } = renderHook(() => useWebSocket(url));
    const ws = MockWebSocket.instances[0];

    act(() => {
      ws.triggerOpen();
    });

    // Send JSON payload
    const jsonPayload = { type: 'TRADE_UPDATE', price: 150.25 };
    act(() => {
      ws.triggerMessage(jsonPayload);
    });

    expect(result.current.data).toEqual(jsonPayload);

    // Send raw string payload
    act(() => {
      ws.triggerMessage('RAW_STRING_MESSAGE');
    });

    expect(result.current.data).toBe('RAW_STRING_MESSAGE');
  });

  it('send() is a no-op when status is not OPEN', () => {
    const url = 'ws://localhost:8080/ws';
    const { result } = renderHook(() => useWebSocket(url));
    const ws = MockWebSocket.instances[0];

    expect(result.current.status).toBe('CONNECTING');

    let sendSuccess;
    act(() => {
      sendSuccess = result.current.send({ test: 123 });
    });

    expect(sendSuccess).toBe(false);
    expect(ws.sentMessages.length).toBe(0);

    // Open connection
    act(() => {
      ws.triggerOpen();
    });

    act(() => {
      sendSuccess = result.current.send({ test: 123 });
    });

    expect(sendSuccess).toBe(true);
    expect(ws.sentMessages).toEqual(['{"test":123}']);
  });

  it('reconnects with exponential backoff on unexpected close', () => {
    const url = 'ws://localhost:8080/ws';
    const { result } = renderHook(() =>
      useWebSocket(url, { initialDelay: 1000, maxRetries: 3, maxDelay: 30000 })
    );

    let ws = MockWebSocket.instances[0];
    act(() => {
      ws.triggerOpen();
    });
    expect(result.current.status).toBe('OPEN');

    // Trigger unexpected close #1 (attempt 0 -> 1000ms delay)
    act(() => {
      ws.triggerClose();
    });
    expect(result.current.status).toBe('CLOSED');
    expect(MockWebSocket.instances.length).toBe(1);

    // Fast-forward 1000ms
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(MockWebSocket.instances.length).toBe(2);

    // Trigger unexpected close #2 (attempt 1 -> 2000ms delay)
    ws = MockWebSocket.instances[1];
    act(() => {
      ws.triggerClose();
    });

    // Fast-forward 2000ms
    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(MockWebSocket.instances.length).toBe(3);

    // Trigger unexpected close #3 (attempt 2 -> 4000ms delay)
    ws = MockWebSocket.instances[2];
    act(() => {
      ws.triggerClose();
    });

    // Fast-forward 4000ms
    act(() => {
      vi.advanceTimersByTime(4000);
    });
    expect(MockWebSocket.instances.length).toBe(4);

    // Trigger unexpected close #4 (attempt 3 >= maxRetries 3 -> no further reconnect)
    ws = MockWebSocket.instances[3];
    act(() => {
      ws.triggerClose();
    });

    act(() => {
      vi.advanceTimersByTime(100000);
    });

    // Retries stopped at maxRetries
    expect(MockWebSocket.instances.length).toBe(4);
  });

  it('resets retry attempt counter on successful onopen connection', () => {
    const url = 'ws://localhost:8080/ws';
    const { result } = renderHook(() =>
      useWebSocket(url, { initialDelay: 1000, maxRetries: 2 })
    );

    let ws = MockWebSocket.instances[0];

    // Unexpected close -> delay 1000ms
    act(() => {
      ws.triggerClose();
    });

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(MockWebSocket.instances.length).toBe(2);

    ws = MockWebSocket.instances[1];
    // Reconnection succeeds!
    act(() => {
      ws.triggerOpen();
    });
    expect(result.current.status).toBe('OPEN');

    // Subsequent unexpected close should start again at initialDelay (1000ms)
    act(() => {
      ws.triggerClose();
    });

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(MockWebSocket.instances.length).toBe(3);
  });

  it('cancels scheduled reconnect when unmounted', () => {
    const url = 'ws://localhost:8080/ws';
    const { unmount } = renderHook(() =>
      useWebSocket(url, { initialDelay: 1000 })
    );

    const ws = MockWebSocket.instances[0];

    act(() => {
      ws.triggerClose();
    });

    // Unmount during delay
    unmount();

    // Fast forward time
    act(() => {
      vi.advanceTimersByTime(5000);
    });

    // No new sockets should have been created
    expect(MockWebSocket.instances.length).toBe(1);
  });
});
