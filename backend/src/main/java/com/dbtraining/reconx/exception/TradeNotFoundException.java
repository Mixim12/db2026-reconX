package com.dbtraining.reconx.exception;

/** TICKET-ADV025 — 404 Not Found: tradeRef has no row in trades. */
public class TradeNotFoundException extends ReconException {
    public TradeNotFoundException(String tradeRef) {
        super("Trade not found: " + tradeRef);
    }

    /** Same decorated message, plus the root cause (e.g. a repository failure) chained through. */
    public TradeNotFoundException(String tradeRef, Throwable cause) {
        super("Trade not found: " + tradeRef, cause);
    }
}
