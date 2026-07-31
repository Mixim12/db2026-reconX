// TICKET-ADV115 — useWebSocket(url, options) with auto-reconnect & exponential backoff
import { useEffect, useRef, useState, useCallback } from 'react';

export function useWebSocket(url, options = {}) {
  const {
    reconnect = true,
    shouldReconnect = reconnect,
    maxRetries = 5,
    initialDelay = 1000,
    maxDelay = 30000,
  } = options;

  const [data, setData] = useState(null);
  const [status, setStatus] = useState('CONNECTING');

  const wsRef = useRef(null);
  const retriesRef = useRef(0);
  const timerRef = useRef(null);
  const cancelledRef = useRef(false);

  useEffect(() => {
    if (!url) {
      setStatus('CLOSED');
      return;
    }

    cancelledRef.current = false;

    function connect() {
      if (cancelledRef.current) return;

      setStatus('CONNECTING');

      try {
        const ws = new WebSocket(url);
        wsRef.current = ws;

        ws.onopen = (event) => {
          if (cancelledRef.current) return;
          setStatus('OPEN');
          retriesRef.current = 0;
          if (options.onOpen) options.onOpen(event);
        };

        ws.onmessage = (event) => {
          if (cancelledRef.current) return;
          let parsedData = event.data;
          try {
            parsedData = JSON.parse(event.data);
          } catch {
            parsedData = event.data;
          }
          setData(parsedData);
          if (options.onMessage) options.onMessage(event, parsedData);
        };

        ws.onerror = (event) => {
          if (cancelledRef.current) return;
          if (options.onError) options.onError(event);
        };

        ws.onclose = (event) => {
          if (cancelledRef.current) return;
          setStatus('CLOSED');
          if (options.onClose) options.onClose(event);

          if (shouldReconnect && retriesRef.current < maxRetries) {
            const attempt = retriesRef.current;
            const delay = Math.min(maxDelay, initialDelay * (2 ** attempt));
            retriesRef.current += 1;

            timerRef.current = setTimeout(() => {
              if (!cancelledRef.current) {
                connect();
              }
            }, delay);
          }
        };
      } catch (err) {
        setStatus('CLOSED');
      }
    }

    connect();

    return () => {
      cancelledRef.current = true;
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      if (wsRef.current) {
        const ws = wsRef.current;
        ws.onopen = null;
        ws.onmessage = null;
        ws.onerror = null;
        ws.onclose = null;
        if (ws.readyState === 0 || ws.readyState === 1) { // CONNECTING or OPEN
          ws.close();
        }
        wsRef.current = null;
      }
    };
  }, [url, shouldReconnect, maxRetries, initialDelay, maxDelay]);

  const send = useCallback((payload) => {
    const ws = wsRef.current;
    const OPEN = typeof WebSocket !== 'undefined' ? WebSocket.OPEN : 1;

    if (ws && ws.readyState === OPEN) {
      const message = typeof payload === 'string' ? payload : JSON.stringify(payload);
      ws.send(message);
      return true;
    }
    return false;
  }, []);

  return { data, status, send };
}
