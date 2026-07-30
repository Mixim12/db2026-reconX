package com.dbtraining.reconx.exception;

/**
 * TICKET-ADV025
 * 422 Unprocessable: the internal and external trade records do not match.
 */
public class ReconciliationMismatchException extends ReconException {
    private final String reconBreakId;

    public ReconciliationMismatchException(String message) { this(message, (String) null); }

    public ReconciliationMismatchException(String message, String reconBreakId) {
        super(message);
        this.reconBreakId = reconBreakId;
    }

    /** Same message, plus the root cause chained through. */
    public ReconciliationMismatchException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public ReconciliationMismatchException(String message, String reconBreakId, Throwable cause) {
        super(message, cause);
        this.reconBreakId = reconBreakId;
    }

    @Override
    public String getReconBreakId() { return reconBreakId; }
}
