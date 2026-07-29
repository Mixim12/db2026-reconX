package com.dbtraining.reconx.exception;

/** TICKET-ADV025 — 422 Unprocessable: internal vs external trade do not match. */
public class ReconciliationMismatchException extends ReconException {
    public ReconciliationMismatchException(String message) { super(message); }

    /** Same message, plus the root cause chained through. */
    public ReconciliationMismatchException(String message, Throwable cause) { super(message, cause); }
}
