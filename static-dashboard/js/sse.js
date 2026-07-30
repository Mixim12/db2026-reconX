// TICKET-ADV106 / ADV107 — EventSource live feed with prepend + slide-in animation.
(function () {
  const feed = document.getElementById('trade-feed');
  if (!feed) return;

  const STREAM_URL = '/api/v1/trades/stream';
  let sse = null;

  function updateConnectionBadge(text, variant) {
    const badge = document.getElementById('sse-status');
    if (!badge) return;
    badge.textContent = text;
    badge.className = 'badge badge--' + variant;
  }

  function prepend(trade) {
    const el = document.createElement('article');
    el.className = 'trade-card trade-card--' + trade.status.toLowerCase();
    el.innerHTML = `
      <strong>${trade.tradeRef}</strong>
      <span> ${trade.symbol} </span>
      <span> qty=${trade.qty} </span>
      <span> price=${trade.price} </span>
      <span> [${trade.status}]</span>`;
    feed.prepend(el);
  }

  function connect() {
    sse = new EventSource(STREAM_URL);

    sse.onopen = () => updateConnectionBadge('Live', 'live');

    sse.onmessage = (event) => {
      try {
        prepend(JSON.parse(event.data));
      } catch (err) {
        console.error('Invalid SSE payload', err);
      }
    };

    sse.onerror = () => updateConnectionBadge('Reconnecting…', 'reconnecting');
  }

  window.addEventListener('beforeunload', () => sse?.close());

  connect();
})();
