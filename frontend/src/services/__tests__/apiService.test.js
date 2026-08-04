// Regression cover for the TradeResponse -> trade view model mapping.
// The backend DTO names the instrument `instrumentSymbol` and the size `quantity`;
// the table renders `symbol` / `qty`. A mismatch here blanks two whole columns.
import { describe, it, expect } from 'vitest';
import { toTradeViewModel } from '../apiService.js';

describe('toTradeViewModel', () => {
  const dto = {
    id: 119,
    tradeRef: 'TRD-2026-000119',
    instrumentId: 7,
    instrumentSymbol: 'SAP.DE',
    counterpartyId: 3,
    counterpartyName: 'Barclays',
    assetClass: 'EQUITY',
    side: 'BUY',
    quantity: '1000.0000',
    price: '118.5688',
    tradeDate: '2026-08-04',
    status: 'MATCHED',
  };

  it('maps instrumentSymbol to symbol and quantity to qty', () => {
    const vm = toTradeViewModel(dto);
    expect(vm.symbol).toBe('SAP.DE');
    expect(vm.qty).toBe(1000);
  });

  it('coerces BigDecimal strings to numbers so toLocaleString works', () => {
    const vm = toTradeViewModel(dto);
    expect(typeof vm.qty).toBe('number');
    expect(typeof vm.price).toBe('number');
    expect(vm.price).toBe(118.5688);
  });

  it('preserves identity and status fields used by the row key and status pill', () => {
    const vm = toTradeViewModel(dto);
    expect(vm.id).toBe(119);
    expect(vm.tradeRef).toBe('TRD-2026-000119');
    expect(vm.status).toBe('MATCHED');
  });

  it('yields null rather than undefined for a missing symbol', () => {
    expect(toTradeViewModel({ ...dto, instrumentSymbol: undefined }).symbol).toBeNull();
  });

  it('keeps null quantity and price null instead of coercing to 0', () => {
    const vm = toTradeViewModel({ ...dto, quantity: null, price: null });
    expect(vm.qty).toBeNull();
    expect(vm.price).toBeNull();
  });
});
