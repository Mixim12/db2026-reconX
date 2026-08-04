// TICKET-ADV112-related — fetch wrapper that attaches Bearer JWT from sessionStorage.
const BASE = '/api';

function authHeaders() {
  const token = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('reconx-token') : null;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request(method, path, body) {
  const headers = {
    'Content-Type': 'application/json',
    ...authHeaders(),
  };
  const options = {
    method,
    headers,
  };
  if (body !== undefined) {
    options.body = JSON.stringify(body);
  }

  const res = await fetch(`${BASE}${path}`, options);
  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    throw new Error(`HTTP ${res.status}: ${detail || res.statusText}`);
  }
  if (res.status === 204) return null;
  return res.json();
}

/**
 * Backend `TradeResponse` names the instrument `instrumentSymbol` and the size
 * `quantity`; the trade table view model calls them `symbol` and `qty`.
 * Normalising here keeps that mismatch at the network boundary instead of
 * leaking two vocabularies into the components.
 *
 * @param {Record<string, unknown>} dto
 * @returns {{ id, tradeRef, symbol, qty, price, status, side, tradeDate, counterpartyName, assetClass }}
 */
export function toTradeViewModel(dto) {
  return {
    id: dto.id,
    tradeRef: dto.tradeRef,
    symbol: dto.instrumentSymbol ?? null,
    qty: dto.quantity != null ? Number(dto.quantity) : null,
    price: dto.price != null ? Number(dto.price) : null,
    status: dto.status,
    side: dto.side,
    tradeDate: dto.tradeDate,
    counterpartyName: dto.counterpartyName,
    assetClass: dto.assetClass,
  };
}

async function listTrades(params = '') {
  const page = await request('GET', `/v1/trades${params}`);
  return {
    ...page,
    items: (page?.items ?? []).map(toTradeViewModel),
  };
}

export const api = {
  login: (email, password)   => request('POST', '/auth/login', { email, password }),
  listTrades,
  createTrade: (req)         => request('POST', '/v1/trades', req),
  updateStatus: (id, status) => request('PATCH', `/v1/trades/${id}/status`, { status }),
  deleteTrade: (id)          => request('DELETE', `/v1/trades/${id}`),
  runRecon: (req)            => request('POST', '/v1/recon/run', req),
  reconResults: (jobId)      => request('GET', `/v1/recon/jobs/${jobId}/results`),
  audit: (tradeRef)          => request('GET', `/v1/audit/trades/${tradeRef}`),
};
