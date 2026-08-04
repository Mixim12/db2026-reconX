// TICKET-ADV121 — Unit tests for useCallback handler stability with memoised TradeRow
import React, { useState, useCallback } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import TradeRow from '../TradeRow.jsx';

describe('TICKET-ADV121 — useCallback on handlers passed to memoised TradeRow', () => {
  const sampleTrade = {
    id: 'TRADE-100',
    tradeRef: 'EQU-100',
    symbol: 'AAPL',
    qty: 500,
    price: 180.5,
    status: 'MATCHED',
  };

  it('preserves handler reference identity across parent re-renders when using useCallback', async () => {
    let renderCount = 0;
    let lastHandlerRef = null;

    const ParentWithUseCallback = () => {
      const [unrelatedCounter, setUnrelatedCounter] = useState(0);
      const [selectedId, setSelectedId] = useState(null);

      renderCount++;

      const handleSelect = useCallback((id) => {
        setSelectedId(id);
      }, []);

      lastHandlerRef = handleSelect;

      return (
        <div>
          <button type="button" onClick={() => setUnrelatedCounter((c) => c + 1)}>
            Increment Unrelated State ({unrelatedCounter})
          </button>
          {selectedId && <span>Selected: {selectedId}</span>}
          <table>
            <tbody>
              <TradeRow trade={sampleTrade} onClick={handleSelect} />
            </tbody>
          </table>
        </div>
      );
    };

    render(<ParentWithUseCallback />);
    const firstHandlerRef = lastHandlerRef;

    // Trigger parent re-render via unrelated state
    const button = screen.getByRole('button');
    await userEvent.click(button);

    expect(renderCount).toBe(2);
    // Handler reference MUST remain strictly equal across parent renders
    expect(lastHandlerRef).toBe(firstHandlerRef);
  });

  it('fails memoisation when using inline arrow functions (demonstrating necessity of useCallback)', async () => {
    let lastHandlerRef1 = null;
    let lastHandlerRef2 = null;

    const ParentWithInlineArrow = () => {
      const [counter, setCounter] = useState(0);

      const inlineHandler = (id) => console.log(id);

      if (!lastHandlerRef1) {
        lastHandlerRef1 = inlineHandler;
      } else {
        lastHandlerRef2 = inlineHandler;
      }

      return (
        <div>
          <button type="button" onClick={() => setCounter((c) => c + 1)}>
            Re-render ({counter})
          </button>
          <table>
            <tbody>
              <TradeRow trade={sampleTrade} onClick={inlineHandler} />
            </tbody>
          </table>
        </div>
      );
    };

    render(<ParentWithInlineArrow />);

    const button = screen.getByRole('button');
    await userEvent.click(button);

    // Inline arrow creates a NEW function reference on every render
    expect(lastHandlerRef1).not.toBe(lastHandlerRef2);
  });
});
