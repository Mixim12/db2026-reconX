package com.dbtraining.reconx.model;

/**
 * BUY (we acquire) / SELL (we dispose). Used across all TradeType impls.
 * Kept as a tiny enum rather than a String so a typo can't survive compile.
 */
public enum Side {
    /** We acquire the instrument. */
    BUY,
    /** We dispose of the instrument. */
    SELL
}
