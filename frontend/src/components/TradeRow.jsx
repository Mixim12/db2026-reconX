// TICKET-ADV119 — React.memo on TradeRow with custom equality function
import React from 'react';

function TradeRowImpl({ trade, onClick }) {
  if (!trade) return null;

  const id = trade.id ?? trade.tradeRef;
  const status = trade.status || 'UNKNOWN';
  const statusClass = status.toLowerCase();

  return (
    <tr
      className={`trade-row trade-row--${statusClass}`}
      onClick={() => onClick && onClick(id)}
      data-testid={`trade-row-${id}`}
    >
      <td>{trade.tradeRef || trade.id}</td>
      <td>{trade.symbol}</td>
      <td>{trade.qty != null ? trade.qty.toLocaleString() : '-'}</td>
      <td>{trade.price != null ? trade.price : '-'}</td>
      <td>
        <span className={`status-pill status-pill--${statusClass}`}>
          {status}
        </span>
      </td>
    </tr>
  );
}

export function arePropsEqual(prevProps, nextProps) {
  // Check onClick callback reference equality
  if (prevProps.onClick !== nextProps.onClick) return false;

  // Reference equality check for trade object
  if (prevProps.trade === nextProps.trade) return true;

  // Handle null/undefined trade objects
  if (!prevProps.trade || !nextProps.trade) {
    return prevProps.trade === nextProps.trade;
  }

  const prev = prevProps.trade;
  const next = nextProps.trade;

  // Compare specific properties that affect rendering (id, status, price, qty, symbol, tradeRef)
  return (
    (prev.id ?? prev.tradeRef) === (next.id ?? next.tradeRef) &&
    prev.status === next.status &&
    prev.price === next.price &&
    prev.qty === next.qty &&
    prev.symbol === next.symbol &&
    prev.tradeRef === next.tradeRef
  );
}

export const TradeRow = React.memo(TradeRowImpl, arePropsEqual);
export default TradeRow;
