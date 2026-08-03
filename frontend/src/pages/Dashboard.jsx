// TICKET-ADV120 — useMemo for portfolio-value calc.
// TICKET-ADV116 — useTradeStream live feed.
// TICKET-ADV127 — Dashboard re-render audit and StatCard memoisation.
import React, { useMemo, Profiler } from 'react';
import { withAuth } from '@components/withAuth.jsx';
import { useTradeStream } from '@hooks/useTradeStream.js';

/*
 * Audit & Culprit Identification (TICKET-ADV127):
 * ------------------------------------------------
 * a) Which components rendered excessively:
 *    - <StatCard /> instances on the Dashboard.
 *
 * b) Why they rendered:
 *    - Unmemoized <StatCard /> function components re-rendered on every Dashboard parent render cycle,
 *      even when their `label` and `value` props were identical.
 *    - Inline trade filter calculations (`trades.filter(...)`) were re-computed on every render.
 *
 * Targeted Optimizations Applied:
 * 1. Wrapped StatCard in `React.memo` to skip re-renders when label & value props are unchanged.
 * 2. Memoized trade aggregations (portfolioValue, matched count, unmatched count) with `useMemo([trades])`.
 */

export const StatCard = React.memo(function StatCard({ label, value }) {
  return (
    <article className="stat-card">
      <h3 className="stat-card__label">{label}</h3>
      <p className="stat-card__value">{value}</p>
    </article>
  );
});

function onRenderCallback(id, phase, actualDuration, baseDuration) {
  if (typeof window !== 'undefined' && window.__ENABLE_PROFILER_LOGS__) {
    // eslint-disable-next-line no-console
    console.log(`[Profiler] ${id} (${phase}): actual=${actualDuration.toFixed(2)}ms, base=${baseDuration.toFixed(2)}ms`);
  }
}

function DashboardContents() {
  const { trades, isConnected } = useTradeStream();

  const portfolioValue = useMemo(
    () => trades.reduce((sum, t) => sum + (t.quantity * t.price || 0), 0),
    [trades]
  );

  const { matched, unmatched } = useMemo(() => {
    let m = 0;
    let u = 0;
    for (const t of trades) {
      if (t.status === 'MATCHED') {
        m++;
      } else if (t.status === 'UNMATCHED' || t.status === 'DISPUTED') {
        u++;
      }
    }
    return { matched: m, unmatched: u };
  }, [trades]);

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

function Dashboard() {
  return (
    <Profiler id="TradeDashboard" onRender={onRenderCallback}>
      <DashboardContents />
    </Profiler>
  );
}

export default withAuth(Dashboard);
