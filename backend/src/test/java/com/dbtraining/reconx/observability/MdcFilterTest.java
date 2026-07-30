package com.dbtraining.reconx.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ============================================================================
 * TICKET-ADV061 — Structured logging with MDC
 *
 * WHAT:    Unit tests for MdcFilter — the servlet filter that seeds SLF4J's MDC
 *          with a per-request correlationId (and optional tradeRef).
 * HOW:     A capturing FilterChain snapshots the MDC contents at the exact
 *          moment the downstream chain runs, which is the only place the values
 *          are observable — the filter clears MDC on the way out.
 * WHY:     Acceptance criteria demand the values be present BEFORE doFilter and
 *          gone after it, so a thread reused by Tomcat cannot inherit a stale
 *          correlation id from the previous request.
 * ============================================================================
 */
class MdcFilterTest {

    private static final String HDR_CORRELATION = "X-Correlation-Id";
    private static final String HDR_TRADE_REF = "X-Trade-Ref";
    private static final String KEY_CORRELATION = "correlationId";
    private static final String KEY_TRADE_REF = "tradeRef";

    private final MdcFilter filter = new MdcFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** Captures the MDC snapshot visible to the downstream chain. */
    private static final class CapturingChain implements FilterChain {
        private Map<String, String> mdcDuringChain;
        private boolean invoked;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            invoked = true;
            Map<String, String> copy = MDC.getCopyOfContextMap();
            mdcDuringChain = copy == null ? new HashMap<>() : new HashMap<>(copy);
        }
    }

    @Test
    @DisplayName("is a component ordered first so MDC is set before other filters log")
    void isComponentOrderedFirst() {
        assertThat(AnnotationUtils.findAnnotation(MdcFilter.class, Component.class)).isNotNull();

        Order order = AnnotationUtils.findAnnotation(MdcFilter.class, Order.class);
        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(1);
    }

    @Test
    @DisplayName("supplied X-Correlation-Id is visible in MDC during the chain")
    void suppliedCorrelationIdReachesMdc() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HDR_CORRELATION, "foo-123");
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
        assertThat(chain.mdcDuringChain).containsEntry(KEY_CORRELATION, "foo-123");
    }

    @Test
    @DisplayName("missing X-Correlation-Id is replaced by a generated UUID")
    void missingCorrelationIdIsGenerated() throws IOException, ServletException {
        CapturingChain chain = new CapturingChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        String generated = chain.mdcDuringChain.get(KEY_CORRELATION);
        assertThat(generated).isNotBlank();
        assertThat(UUID.fromString(generated)).hasToString(generated);
    }

    @Test
    @DisplayName("blank X-Correlation-Id is treated as absent and replaced by a UUID")
    void blankCorrelationIdIsGenerated() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HDR_CORRELATION, "   ");
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        String generated = chain.mdcDuringChain.get(KEY_CORRELATION);
        assertThat(generated).isNotBlank().doesNotContain(" ");
        assertThat(UUID.fromString(generated)).hasToString(generated);
    }

    @Test
    @DisplayName("X-Trade-Ref is copied into MDC when present")
    void tradeRefReachesMdcWhenPresent() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HDR_CORRELATION, "foo-123");
        request.addHeader(HDR_TRADE_REF, "TRD-0001");
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.mdcDuringChain)
                .containsEntry(KEY_CORRELATION, "foo-123")
                .containsEntry(KEY_TRADE_REF, "TRD-0001");
    }

    @Test
    @DisplayName("absent X-Trade-Ref leaves no tradeRef key in MDC")
    void tradeRefAbsentLeavesKeyUnset() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HDR_CORRELATION, "foo-123");
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.mdcDuringChain).doesNotContainKey(KEY_TRADE_REF);
    }

    @Test
    @DisplayName("MDC is cleared after a successful request")
    void mdcClearedAfterSuccess() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HDR_CORRELATION, "foo-123");
        request.addHeader(HDR_TRADE_REF, "TRD-0001");

        filter.doFilter(request, new MockHttpServletResponse(), new CapturingChain());

        assertThat(MDC.get(KEY_CORRELATION)).isNull();
        assertThat(MDC.get(KEY_TRADE_REF)).isNull();
    }

    @Test
    @DisplayName("MDC is cleared even when the downstream chain throws")
    void mdcClearedWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HDR_CORRELATION, "foo-123");
        FilterChain exploding = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), exploding))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get(KEY_CORRELATION)).isNull();
    }
}
