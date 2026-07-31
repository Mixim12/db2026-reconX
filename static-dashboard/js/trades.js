(function () {
  const table = document.getElementById('trades-table');
  const tbody = document.getElementById('trades-tbody');
  if (!table || !tbody) return;

  let rows = [];

  function renderRows() {
    tbody.innerHTML = rows.map(r => `
      <tr>
        <td>${r.tradeRef}</td>
        <td>${r.symbol}</td>
        <td>${r.quantity}</td>
        <td>${r.price}</td>
        <td>${r.status}</td>
      </tr>`).join('');
  }

  table.querySelectorAll('thead th').forEach(th => {
    th.addEventListener('click', (e) => {
      if (e.target.classList.contains('resize-handle')) return;

      const col = th.dataset.col;
      const type = th.dataset.type || 'string';
      const dir = th.getAttribute('aria-sort') === 'ascending' ? 'descending' : 'ascending';

      table.querySelectorAll('thead th').forEach(other => other.removeAttribute('aria-sort'));
      th.setAttribute('aria-sort', dir);

      const mult = dir === 'ascending' ? 1 : -1;
      rows.sort((a, b) => {
        if (type === 'number') return (Number(a[col]) - Number(b[col])) * mult;
        return String(a[col]).localeCompare(String(b[col])) * mult;
      });

      renderRows();
    });
  });

  table.querySelectorAll('.resize-handle').forEach(handle => {
    handle.addEventListener('mousedown', (e) => {
      e.preventDefault();

      const th = handle.closest('th');
      const startX = e.clientX;
      const startWidth = th.offsetWidth;

      function onMove(ev) {
        th.style.width = (startWidth + ev.clientX - startX) + 'px';
      }

      function onUp() {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
      }

      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });
  });

  fetch('/api/v1/trades?size=200')
    .then(r => r.json())
    .then(data => { rows = data.content || data; renderRows(); })
    .catch(() => {
      rows = [
        { tradeRef: 'EQU-20260603-0001', symbol: 'SAP.DE', quantity: 1000, price: 125.50, status: 'MATCHED' },
        { tradeRef: 'FX-20260603-0001', symbol: 'EUR/USD', quantity: 1000000, price: 1.0852, status: 'PENDING' },
        { tradeRef: 'EQU-20260603-0002', symbol: 'AAPL', quantity: 500, price: 178.20, status: 'BREAK' },
        { tradeRef: 'EQU-20260603-0003', symbol: 'NVDA', quantity: 750, price: 120.40, status: 'MATCHED' },
        { tradeRef: 'FX-20260603-0002', symbol: 'GBP/USD', quantity: 500000, price: 1.2640, status: 'BREAK' }
      ];
      renderRows();
    });
})();
