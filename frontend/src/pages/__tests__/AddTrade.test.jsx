// TICKET-ADV123 — Unit and integration tests for AddTrade form (RHF + Yup)
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock AuthContext & withAuth
vi.mock('@components/withAuth.jsx', () => ({
  withAuth: (Component) => Component,
}));

// Mock apiService
vi.mock('@services/apiService.js', () => ({
  api: {
    createTrade: vi.fn(),
  },
}));

import AddTrade from '../AddTrade.jsx';
import { api } from '@services/apiService.js';

describe('AddTrade Form (TICKET-ADV123)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders trade form fields', () => {
    render(<AddTrade />);

    expect(screen.getByLabelText(/trade ref/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/instrument/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/quantity/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/price/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/trade date/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /submit/i })).toBeInTheDocument();
  });

  it('displays validation errors with role="alert" when submitting empty or invalid form', async () => {
    render(<AddTrade />);

    const submitButton = screen.getByRole('button', { name: /submit/i });
    await userEvent.click(submitButton);

    // Should NOT call API on invalid submit
    expect(api.createTrade).not.toHaveBeenCalled();

    // Validation errors should appear with role="alert"
    const alerts = await screen.findAllByRole('alert');
    expect(alerts.length).toBeGreaterThan(0);
    expect(screen.getByText(/trade ref is required/i)).toBeInTheDocument();
    expect(screen.getByText(/instrument is required/i)).toBeInTheDocument();
    expect(screen.getByText(/counterparty is required/i)).toBeInTheDocument();
  });

  it('displays regex pattern validation error for invalid tradeRef', async () => {
    render(<AddTrade />);

    const tradeRefInput = screen.getByLabelText(/trade ref/i);
    await userEvent.type(tradeRefInput, 'invalid-ref');
    await userEvent.tab();

    const submitButton = screen.getByRole('button', { name: /submit/i });
    await userEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/trade ref must match AAA-YYYYMMDD-NNNN/i)).toBeInTheDocument();
    });
    expect(api.createTrade).not.toHaveBeenCalled();
  });

  it('submits valid form data with parsed numbers to api.createTrade', async () => {
    api.createTrade.mockResolvedValueOnce({ id: 123 });
    render(<AddTrade />);

    await userEvent.type(screen.getByLabelText(/trade ref/i), 'EQU-20260603-0001');
    await userEvent.selectOptions(screen.getByLabelText(/instrument/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/counterparty/i), '1');
    await userEvent.selectOptions(screen.getByLabelText(/asset class/i), 'EQUITY');
    await userEvent.selectOptions(screen.getByLabelText(/side/i), 'BUY');
    await userEvent.type(screen.getByLabelText(/quantity/i), '1000');
    await userEvent.type(screen.getByLabelText(/price/i), '125.50');
    await userEvent.type(screen.getByLabelText(/trade date/i), '2026-06-03');

    const submitButton = screen.getByRole('button', { name: /submit/i });
    await userEvent.click(submitButton);

    await waitFor(() => {
      expect(api.createTrade).toHaveBeenCalledTimes(1);
    });

    expect(api.createTrade).toHaveBeenCalledWith({
      tradeRef: 'EQU-20260603-0001',
      instrumentId: 1,
      counterpartyId: 1,
      assetClass: 'EQUITY',
      side: 'BUY',
      quantity: 1000, // Number, not string!
      price: 125.5,   // Number, not string!
      tradeDate: '2026-06-03',
    });

    expect(await screen.findByText(/trade created successfully!/i)).toBeInTheDocument();
  });
});
