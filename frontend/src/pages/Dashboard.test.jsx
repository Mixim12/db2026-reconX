// TICKET-ADV125 — RTL test: dashboard summary cards
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '@context/ThemeContext.jsx';
import { AuthContext } from '@context/AuthContext.jsx';
import Dashboard from './Dashboard.jsx';

const trades = [
  { id: 1, tradeRef: 'TRD-2026-0001', instrument: 'SAP.DE', quantity: 100, price: 250, status: 'MATCHED' },
  { id: 2, tradeRef: 'TRD-2026-0002', instrument: 'SAP.DE', quantity: 50, price: 251, status: 'UNMATCHED' },
];

// Dashboard reads live trades from useTradeStream (real EventSource,
// TICKET-ADV116), which jsdom doesn't implement. Mock the hook so the
// test exercises rendering only, not a real SSE connection.
vi.mock('@hooks/useTradeStream.js', () => ({
  useTradeStream: () => ({ trades, isConnected: true }),
}));

function renderWithProviders(ui) {
  const user = { email: 'trader@db.com', role: 'TRADER' };
  return render(
    <AuthContext.Provider value={{ user, isLoading: false }}>
      <ThemeProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </ThemeProvider>
    </AuthContext.Provider>
  );
}

describe('<Dashboard />', () => {
  it('shows summary cards', () => {
    renderWithProviders(<Dashboard trades={trades} />);

    expect(screen.getByRole('heading', { name: /portfolio value/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /matched trades/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /unmatched trades/i })).toBeInTheDocument();
    // 100 * 250 + 50 * 251 = 37550
    expect(screen.getByText(/37,550/)).toBeInTheDocument();
  });
});
