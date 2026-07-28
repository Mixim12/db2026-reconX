package com.dbtraining.reconx.exception;

/** 404 Not Found: the given {@code tradeRef} has no matching row in {@code trades}. */
public class TradeNotFoundException extends ReconException {
    /** @param tradeRef the trade reference that could not be found */
    public TradeNotFoundException(String tradeRef) {
        super("Trade not found: " + tradeRef);
    }

    /** Same decorated message, plus the root cause (e.g. a repository failure) chained through. */
    public TradeNotFoundException(String tradeRef, Throwable cause) {
        super("Trade not found: " + tradeRef, cause);
    }
}
