package com.dbtraining.reconx.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV053 — TradeRequest carries the JSR-380 wire contract.
 */
class TradeRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static TradeRequest valid() {
        return new TradeRequest(
                "ABC-20260130-0001",
                1L,
                2L,
                "EQUITY",
                "BUY",
                new BigDecimal("100.0000"),
                new BigDecimal("12.3456"),
                LocalDate.now());
    }

    private static Set<String> violatedPaths(TradeRequest req) {
        return validator.validate(req).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("a fully populated request has no violations")
    void validRequestPasses() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    @DisplayName("blank tradeRef is rejected (@NotBlank)")
    void blankTradeRefRejected() {
        TradeRequest req = new TradeRequest("   ", 1L, 2L, "EQUITY", "BUY",
                BigDecimal.ONE, BigDecimal.ONE, LocalDate.now());
        assertThat(violatedPaths(req)).contains("tradeRef");
    }

    @Test
    @DisplayName("null ids and null tradeDate are rejected (@NotNull)")
    void nullFieldsRejected() {
        TradeRequest req = new TradeRequest("ABC-20260130-0001", null, null, "EQUITY", "BUY",
                BigDecimal.ONE, BigDecimal.ONE, null);
        assertThat(violatedPaths(req))
                .contains("instrumentId", "counterpartyId", "tradeDate");
    }

    @Test
    @DisplayName("zero and negative quantity are rejected (@DecimalMin exclusive)")
    void nonPositiveQuantityRejected() {
        assertThat(violatedPaths(new TradeRequest("ABC-20260130-0001", 1L, 2L, "EQUITY", "BUY",
                BigDecimal.ZERO, BigDecimal.ONE, LocalDate.now())))
                .contains("quantity");

        assertThat(violatedPaths(new TradeRequest("ABC-20260130-0001", 1L, 2L, "EQUITY", "BUY",
                new BigDecimal("-1"), BigDecimal.ONE, LocalDate.now())))
                .contains("quantity");
    }

    @Test
    @DisplayName("a future tradeDate is rejected (@PastOrPresent)")
    void futureTradeDateRejected() {
        TradeRequest req = new TradeRequest("ABC-20260130-0001", 1L, 2L, "EQUITY", "BUY",
                BigDecimal.ONE, BigDecimal.ONE, LocalDate.now().plusDays(1));
        assertThat(violatedPaths(req)).contains("tradeDate");
    }

    @Test
    @DisplayName("record declares @NotBlank, @NotNull, @DecimalMin and @PastOrPresent")
    void declaresRequiredConstraintAnnotations() {
        assertThat(TradeRequest.class.isRecord()).isTrue();

        Set<Class<? extends Annotation>> declared = Arrays.stream(TradeRequest.class.getDeclaredFields())
                .flatMap(f -> Arrays.stream(f.getAnnotations()))
                .map(Annotation::annotationType)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(declared).contains(NotBlank.class, NotNull.class, DecimalMin.class, PastOrPresent.class);
    }
}
