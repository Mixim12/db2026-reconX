// TICKET-ADV114 — Compound DataTable.
// TICKET-ADV117 — useDebouncedSearch.
// TICKET-ADV119 / TICKET-ADV121 — TradeRow memoisation & useCallback handler
import React, { useState, useEffect, useCallback } from 'react';
import { withAuth } from '@components/withAuth.jsx';
import DataTable from '@components/DataTable.jsx';
import TradeRow from '@components/TradeRow.jsx';
import { useDebouncedSearch } from '@hooks/useDebouncedSearch.js';
import { api } from '@services/apiService.js';

function Trades() {
  const [search, setSearch] = useState('');
  const debounced = useDebouncedSearch(search, 300);
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [data, setData] = useState({ items: [], totalPages: 0 });

  useEffect(() => {
    const params = new URLSearchParams({ page: String(page) });
    if (debounced) params.set('status', debounced);
    if (sort) params.set('sort', sort);

    api.listTrades(`?${params.toString()}`)
      .then((res) => setData({ items: res.items ?? [], totalPages: res.totalPages ?? 0 }))
      .catch(() => setData({ items: [], totalPages: 0 }));
  }, [page, debounced, sort]);

  // TICKET-ADV121: Reference-stable handler for memoised <TradeRow /> child components
  const handleSelectTrade = useCallback((id) => {
    setSelectedId(id);
  }, []);

  return (
    <section>
      <h2>Trades</h2>
      <input
        aria-label="Filter by status"
        placeholder="status filter (PENDING/MATCHED/…)"
        value={search}
        onChange={(e) => setSearch(e.target.value.toUpperCase())}
      />
      {selectedId && <p data-testid="selected-trade">Selected Trade: {selectedId}</p>}
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
        <DataTable.Pagination
          page={page}
          totalPages={Math.max(1, data.totalPages)}
          onChange={setPage}
        />
      </DataTable>
    </section>
  );
}

export default withAuth(Trades);
