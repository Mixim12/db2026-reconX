package com.dbtraining.reconx.observability;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * ============================================================================
 * TICKET-ADV061 — Structured logging with MDC
 *
 * WHAT:    Seeds SLF4J's MDC with a per-request correlationId and, when the
 *          caller supplies one, a tradeRef — the two keys logback-spring.xml
 *          renders via %X{correlationId:-} / %X{tradeRef:-} in dev and emits as
 *          JSON fields under uat/prod.
 * HOW:     @Order(1) puts this ahead of every other filter so downstream logs
 *          already carry the id. X-Correlation-Id is honoured when present and
 *          non-blank, otherwise a UUID is minted so no request goes unlabelled.
 * WHY:     MDC is a ThreadLocal map and Tomcat reuses request threads, so the
 *          finally-block MDC.clear() is load-bearing: without it a correlation
 *          id leaks into the next, unrelated request on the same thread.
 * OBSERVE: curl -H "X-Correlation-Id: foo-123" .../api/v1/trades under `dev`
 *          logs every line of that request with foo-123 in the MDC slot.
 * ============================================================================
 */
@Component
@Order(1)
public class MdcFilter implements Filter {

    static final String HDR_CORRELATION_ID = "X-Correlation-Id";
    static final String HDR_TRADE_REF = "X-Trade-Ref";

    static final String MDC_CORRELATION_ID = "correlationId";
    static final String MDC_TRADE_REF = "tradeRef";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest http = (HttpServletRequest) request;
        String correlationId = header(http, HDR_CORRELATION_ID, UUID.randomUUID().toString());
        String tradeRef = header(http, HDR_TRADE_REF, null);

        try {
            MDC.put(MDC_CORRELATION_ID, correlationId);
            if (tradeRef != null) {
                MDC.put(MDC_TRADE_REF, tradeRef);
            }
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String header(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
