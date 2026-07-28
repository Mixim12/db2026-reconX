package com.dbtraining.reconx.exception;

/**
 * 400 Bad Request: a trade failed business validation (e.g. a Builder
 * invariant or a JSR-380 constraint on the inbound DTO).
 */
public class InvalidTradeException extends ReconException {
    /** @param message a human-readable description of which validation failed and why */
    public InvalidTradeException(String message) { super(message); }

    /** Same message, plus the root cause (e.g. a parse failure) chained through. */
    public InvalidTradeException(String message, Throwable cause) { super(message, cause); }
}
