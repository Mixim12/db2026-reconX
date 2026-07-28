package com.dbtraining.reconx.exception;

/** TICKET-ADV025 — 409 Conflict: tradeRef already exists. */
public class DuplicateTradeRefException extends ReconException {
    public DuplicateTradeRefException(String tradeRef) {
        super("Duplicate tradeRef: " + tradeRef);
    }

    /** Same decorated message, plus the root cause (e.g. a unique-constraint violation) chained through. */
    public DuplicateTradeRefException(String tradeRef, Throwable cause) {
        super("Duplicate tradeRef: " + tradeRef, cause);
    }
}
