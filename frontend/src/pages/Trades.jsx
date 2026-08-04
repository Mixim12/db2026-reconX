// TICKET-ADV114 — Compound DataTable.
// TICKET-ADV119 / TICKET-ADV121 — TradeRow memoisation & useCallback handler
import React, { useState, useEffect, useCallback } from 'react';
import { withAuth } from '@components/withAuth.jsx';
import DataTable from '@components/DataTable.jsx';
import TradeRow from '@components/TradeRow.jsx';
import { api } from '@services/apiService.js';

function Trades() {
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState(null); // { field, dir }
  const [selectedId, setSelectedId] = useState(null);
  const [data, setData] = useState({ items: [], totalPages: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    const params = new URLSearchParams({ page: String(page) });
    if (statusFilter) params.set('status', statusFilter);
    if (sort) {
      let backendSort = sort.field;
      if (sort.field === 'symbol') backendSort = 'instrument';
      if (sort.field === 'qty') backendSort = 'quantity';
      params.set('sort', `${backendSort},${sort.dir}`);
    }

    api.listTrades(`?${params.toString()}`)
      .then((res) => {
        setData({ items: res.items ?? [], totalPages: res.totalPages ?? 0 });
        setError(null);
      })
      .catch((err) => {
        setData({ items: [], totalPages: 0 });
        setError(err.message || 'Failed to load trades');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [page, statusFilter, sort]);

  // TICKET-ADV121: Reference-stable handler for memoised <TradeRow /> child components
  const handleSelectTrade = useCallback((id) => {
    setSelectedId(id);
  }, []);

  return (
    <section>
      <h2>Trades</h2>
      <select
        aria-label="Filter by status"
        value={statusFilter}
        onChange={(e) => {
          setStatusFilter(e.target.value);
          setPage(0);
        }}
      >
        <option value="">All Statuses</option>
        <option value="PENDING">PENDING</option>
        <option value="MATCHED">MATCHED</option>
        <option value="UNMATCHED">UNMATCHED</option>
        <option value="BREAK">BREAK</option>
        <option value="CANCELLED">CANCELLED</option>
      </select>
      
      {selectedId && <p data-testid="selected-trade">Selected Trade: {selectedId}</p>}
      
      {error && <div className="error-fallback" role="alert">{error}</div>}
      
      <DataTable sort={sort} onSortChange={setSort}>
        <DataTable.Header columns={[
          { key: 'tradeRef', label: 'Ref' },
          { key: 'symbol',   label: 'Symbol' },
          { key: 'qty',      label: 'Qty' },
          { key: 'price',    label: 'Price' },
          { key: 'status',   label: 'Status' },
        ]} />
        <DataTable.Body
          rows={data.items}
          render={(t) => (
            <TradeRow key={t.id || t.tradeRef} trade={t} onClick={handleSelectTrade} />
          )}
        />
        {loading && <div className="loader">Loading...</div>}
        <DataTable.Pagination
          page={page}
          totalPages={data.totalPages}
          onChange={setPage}
        />
      </DataTable>
    </section>
  );
}

export default withAuth(Trades);
