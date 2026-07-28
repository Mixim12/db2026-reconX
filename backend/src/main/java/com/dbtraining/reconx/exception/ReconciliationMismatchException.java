package com.dbtraining.reconx.exception;

/**
 * TICKET-ADV025
 * 422 Unprocessable: the internal and external trade records do not match.
 */
public class ReconciliationMismatchException extends ReconException {
    /** @param message a human-readable description of the mismatch */
    public ReconciliationMismatchException(String message) { super(message); }

    /** Same message, plus the root cause chained through. */
    public ReconciliationMismatchException(String message, Throwable cause) { super(message, cause); }
}
