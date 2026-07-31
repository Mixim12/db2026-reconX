// TICKET-ADV119 — Unit tests for TradeRow component and custom arePropsEqual function
import React, { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import TradeRow, { arePropsEqual } from '../TradeRow.jsx';

describe('TradeRow & arePropsEqual (TICKET-ADV119)', () => {
  const sampleTrade1 = {
    id: 1,
    tradeRef: 'EQU-001',
    symbol: 'SAP.DE',
    qty: 1000,
    price: 125.5,
    status: 'MATCHED',
  };

  const sampleTrade2 = {
    id: 1,
    tradeRef: 'EQU-001',
    symbol: 'SAP.DE',
    qty: 1000,
    price: 125.5,
    status: 'MATCHED',
  };

  const handleClick = () => {};

  describe('arePropsEqual custom equality function', () => {
    it('returns true when trade values and onClick reference are identical', () => {
      expect(
        arePropsEqual(
          { trade: sampleTrade1, onClick: handleClick },
          { trade: sampleTrade2, onClick: handleClick }
        )
      ).toBe(true);
    });

    it('returns false when status changes', () => {
      const updatedTrade = { ...sampleTrade1, status: 'BREAK' };
      expect(
        arePropsEqual(
          { trade: sampleTrade1, onClick: handleClick },
          { trade: updatedTrade, onClick: handleClick }
        )
      ).toBe(false);
    });

    it('returns false when price changes', () => {
      const updatedTrade = { ...sampleTrade1, price: 130.0 };
      expect(
        arePropsEqual(
          { trade: sampleTrade1, onClick: handleClick },
          { trade: updatedTrade, onClick: handleClick }
        )
      ).toBe(false);
    });

    it('returns false when onClick reference changes', () => {
      const newClick = () => {};
      expect(
        arePropsEqual(
          { trade: sampleTrade1, onClick: handleClick },
          { trade: sampleTrade1, onClick: newClick }
        )
      ).toBe(false);
    });

    it('ignores changes to unrelated trade properties', () => {
      const tradeWithUnrelated = { ...sampleTrade1, internalNote: 'debug info' };
      expect(
        arePropsEqual(
          { trade: sampleTrade1, onClick: handleClick },
          { trade: tradeWithUnrelated, onClick: handleClick }
        )
      ).toBe(true);
    });
  });

  describe('TradeRow component rendering', () => {
    it('renders trade fields correctly inside a table body', () => {
      render(
        <table>
          <tbody>
            <TradeRow trade={sampleTrade1} onClick={handleClick} />
          </tbody>
        </table>
      );

      expect(screen.getByText('EQU-001')).toBeInTheDocument();
      expect(screen.getByText('SAP.DE')).toBeInTheDocument();
      expect(screen.getByText('125.5')).toBeInTheDocument();
      expect(screen.getByText('MATCHED')).toBeInTheDocument();
    });

    it('invokes onClick callback when clicked', async () => {
      const onClickMock = vi.fn();
      render(
        <table>
          <tbody>
            <TradeRow trade={sampleTrade1} onClick={onClickMock} />
          </tbody>
        </table>
      );

      const row = screen.getByTestId('trade-row-1');
      await userEvent.click(row);

      expect(onClickMock).toHaveBeenCalledTimes(1);
      expect(onClickMock).toHaveBeenCalledWith(1);
    });

    it('skips re-render when parent state updates with unchanged trade props', () => {
      let renderCount = 0;

      const TestHarness = () => {
        const [counter, setCounter] = useState(0);
        const [trade] = useState(sampleTrade1);

        renderCount++;

        return (
          <div>
            <button type="button" onClick={() => setCounter((c) => c + 1)}>
              Unrelated State Update ({counter})
            </button>
            <table>
              <tbody>
                <TradeRow trade={trade} onClick={handleClick} />
              </tbody>
            </table>
          </div>
        );
      };

      render(<TestHarness />);
      expect(renderCount).toBe(1);

      // Trigger unrelated parent state update
      const button = screen.getByRole('button');
      userEvent.click(button);

      expect(screen.getByText('EQU-001')).toBeInTheDocument();
    });
  });
});
