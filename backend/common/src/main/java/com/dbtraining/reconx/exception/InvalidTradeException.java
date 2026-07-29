package com.dbtraining.reconx.exception;

/** TICKET-ADV025 — 400 Bad Request: a trade failed business validation. */
public class InvalidTradeException extends ReconException {
    public InvalidTradeException(String message) { super(message); }

    /** Same message, plus the root cause (e.g. a parse failure) chained through. */
    public InvalidTradeException(String message, Throwable cause) { super(message, cause); }
}
