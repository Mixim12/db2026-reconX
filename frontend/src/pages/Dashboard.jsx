// TICKET-ADV120 — useMemo for portfolio-value calc.
// TICKET-ADV116 — useTradeStream live feed.
import React, { useMemo } from 'react';
import { withAuth } from '@components/withAuth.jsx';
import { useTradeStream } from '@hooks/useTradeStream.js';

function StatCard({ label, value }) {
  return (
    <article className="stat-card">
      <h3 className="stat-card__label">{label}</h3>
      <p className="stat-card__value">{value}</p>
    </article>
  );
}

function Dashboard() {
  const { trades, isConnected } = useTradeStream();

  const portfolioValue = useMemo(
    () => trades.reduce((sum, t) => sum + (t.quantity * t.price || 0), 0),
    [trades]
  );

  const matched = trades.filter((t) => t.status === 'MATCHED').length;
  const unmatched = trades.filter((t) => ['UNMATCHED', 'DISPUTED'].includes(t.status)).length;

  return (
    <section>
      <h2>Dashboard</h2>
      <div className="stat-grid">
        <StatCard label="Portfolio value" value={portfolioValue.toLocaleString('en-US')} />
        <StatCard label="Trades streamed" value={trades.length} />
        <StatCard label="Matched trades" value={matched} />
        <StatCard label="Unmatched trades" value={unmatched} />
      </div>
      <div role="status" aria-live="polite">
        SSE: {isConnected ? 'connected' : 'disconnected'}
      </div>
    </section>
  );
}

export default withAuth(Dashboard);
