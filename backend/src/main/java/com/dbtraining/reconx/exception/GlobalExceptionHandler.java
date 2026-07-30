package com.dbtraining.reconx.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TradeNotFoundException.class)
    public ProblemDetail notFound(TradeNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setType(URI.create("https://reconx.dbtraining.com/errors/trade-not-found"));
        problem.setTitle("Trade not found");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DuplicateTradeRefException.class)
    public ProblemDetail duplicate(DuplicateTradeRefException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setType(URI.create("https://reconx.dbtraining.com/errors/duplicate-trade-ref"));
        problem.setTitle("Duplicate trade reference");
        return problem;
    }

    /**
     * TICKET-ADV072 — a failed login is 401, and the detail says only that the
     * credentials were rejected: no hint about which half was wrong.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail badCredentials(BadCredentialsException exception) {
        log.warn("Rejected login attempt");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Invalid credentials");
        problem.setType(URI.create("https://reconx.dbtraining.com/errors/invalid-credentials"));
        problem.setTitle("Invalid credentials");
        return problem;
    }

    @ExceptionHandler(InvalidTradeException.class)
    public ProblemDetail invalid(InvalidTradeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setType(URI.create("https://reconx.dbtraining.com/errors/invalid-trade"));
        problem.setTitle("Invalid trade");
        return problem;
    }

    @ExceptionHandler(ReconciliationMismatchException.class)
    public ProblemDetail mismatch(ReconciliationMismatchException exception) {
        ProblemDetail problem = reconciliationProblem(exception);
        problem.setProperty("reconBreakId", exception.getReconBreakId());
        return problem;
    }

    @ExceptionHandler(ReconException.class)
    public ProblemDetail recon(ReconException exception) {
        ProblemDetail problem = reconciliationProblem(exception);
        problem.setProperty("reconBreakId", exception.getReconBreakId());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation failed");
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraint(ConstraintViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAny(Exception exception) {
        log.error("Unhandled exception", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred — please contact support with the correlationId");
        problem.setTitle("Internal server error");
        return problem;
    }

    private static ProblemDetail reconciliationProblem(ReconException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setType(URI.create("https://reconx.dbtraining.com/errors/recon-failure"));
        problem.setTitle("Reconciliation failure");
        return problem;
    }
}
