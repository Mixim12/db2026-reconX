// TICKET-ADV105 — SSE handler with prepend-and-animate + DOM bounding (cap at 50)
(function () {
  const feed = document.getElementById('trade-feed');
  if (!feed) return;

  const MAX_CARDS = 50;

  function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function prependTradeCard(trade) {
    if (!trade) return;

    const card = document.createElement('article');
    const status = (trade.status || 'MATCHED').toUpperCase();
    const statusClass = status.toLowerCase();

    card.className = `trade-card trade-card--${statusClass} trade-card--new`;

    const timestamp = trade.timestamp || new Date().toLocaleTimeString();
    const tradeRef = escapeHtml(trade.tradeRef || `TRADE-${Date.now().toString().slice(-4)}`);
    const symbol = escapeHtml(trade.symbol || 'N/A');
    const qty = trade.qty != null ? trade.qty.toLocaleString() : '0';
    const price = trade.price != null ? trade.price : '0.00';

    card.innerHTML = `
      <strong>${tradeRef}</strong>
      <span>${symbol}</span>
      <span>qty=${qty}</span>
      <span>price=${price}</span>
      <span><time>${timestamp}</time></span>
      <span>[${status}]</span>
    `;

    // Entrance animation lifecycle cleanup
    function handleAnimationEnd() {
      card.classList.remove('trade-card--new');
      card.removeEventListener('animationend', handleAnimationEnd);
    }
    card.addEventListener('animationend', handleAnimationEnd, { once: true });

    // Fallback timer to guarantee cleanup of animation modifier class
    setTimeout(() => {
      card.classList.remove('trade-card--new');
    }, 400);

    // Prepend newest trade card at top of feed
    feed.prepend(card);

    // DOM Bounding: Cap at 50 cards max
    while (feed.children.length > MAX_CARDS) {
      if (feed.lastElementChild) {
        feed.lastElementChild.remove();
      }
    }
  }

  // Connect to SSE stream if available, otherwise run simulated demo stream
  let sse = null;
  const STREAM_URL = '/api/v1/trades/stream';

  function initSSE() {
    try {
      sse = new EventSource(STREAM_URL);

      sse.onopen = function () {
        console.log('[SSE] Connected to trade stream');
      };

      sse.onmessage = function (event) {
        try {
          const trade = JSON.parse(event.data);
          prependTradeCard(trade);
        } catch (err) {
          console.error('[SSE] Failed to parse trade payload:', err);
        }
      };

      sse.onerror = function () {
        console.warn('[SSE] Connection lost. Reconnecting...');
        // EventSource automatically handles reconnection with backoff
      };
    } catch (e) {
      console.warn('[SSE] EventSource unavailable, running demo stream');
      runDemoStream();
    }
  }

  const demoEvents = [
    { tradeRef: 'EQU-20260603-0001', symbol: 'SAP.DE',  qty: 1000, price: 125.50, status: 'MATCHED' },
    { tradeRef: 'FX-20260603-0001',  symbol: 'EUR/USD', qty: 1000000, price: 1.0852, status: 'PENDING' },
    { tradeRef: 'EQU-20260603-0002', symbol: 'AAPL',    qty: 500,  price: 178.20, status: 'BREAK' },
    { tradeRef: 'EQU-20260603-0003', symbol: 'NVDA',    qty: 750,  price: 120.40, status: 'MATCHED' },
    { tradeRef: 'FX-20260603-0002',  symbol: 'GBP/USD', qty: 500000, price: 1.2640, status: 'BREAK' },
  ];

  function runDemoStream() {
    demoEvents.forEach((e, i) => setTimeout(() => prependTradeCard(e), 500 * i));
  }

  // Check if server returns valid EventSource, fallback to demo if failed
  if (typeof EventSource !== 'undefined' && location.protocol !== 'file:') {
    initSSE();
    setTimeout(() => {
      if (feed.children.length === 0) {
        runDemoStream();
      }
    }, 1500);
  } else {
    runDemoStream();
  }

  // Cleanup on unload
  window.addEventListener('beforeunload', () => {
    if (sse) sse.close();
  });

  // Expose helper globally for manual DevTools testing
  window.prependTradeCard = prependTradeCard;
})();
