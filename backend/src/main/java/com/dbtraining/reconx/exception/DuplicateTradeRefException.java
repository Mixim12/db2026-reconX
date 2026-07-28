package com.dbtraining.reconx.exception;

/** 409 Conflict: a trade with this {@code tradeRef} already exists. */
public class DuplicateTradeRefException extends ReconException {
    /** @param tradeRef the trade reference that already exists */
    public DuplicateTradeRefException(String tradeRef) {
        super("Duplicate tradeRef: " + tradeRef);
    }

    /** Same decorated message, plus the root cause (e.g. a unique-constraint violation) chained through. */
    public DuplicateTradeRefException(String tradeRef, Throwable cause) {
        super("Duplicate tradeRef: " + tradeRef, cause);
    }
}
