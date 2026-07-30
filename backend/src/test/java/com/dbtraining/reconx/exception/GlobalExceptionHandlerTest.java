package com.dbtraining.reconx.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.lang.reflect.Method;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsTradeNotFoundToProblemDetail404() {
        ProblemDetail problem = handler.notFound(new TradeNotFoundException("TR-1"));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getType()).isEqualTo(URI.create(
                "https://reconx.dbtraining.com/errors/trade-not-found"));
        assertThat(problem.getTitle()).isEqualTo("Trade not found");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void mapsDuplicateReferenceToProblemDetail409() {
        ProblemDetail problem = handler.duplicate(new DuplicateTradeRefException("TR-1"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getType()).isEqualTo(URI.create(
                "https://reconx.dbtraining.com/errors/duplicate-trade-ref"));
    }

    @Test
    void mapsInvalidTradeToBadRequest() {
        ProblemDetail problem = handler.invalid(new InvalidTradeException("bad trade"));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("bad trade");
    }

    @Test
    void mapsReconMismatchToUnprocessableEntityWithBreakId() {
        ProblemDetail problem = handler.mismatch(
                new ReconciliationMismatchException("values differ", "RB-42"));

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getProperties()).containsEntry("reconBreakId", "RB-42");
    }

    @Test
    void joinsAllValidationFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(
                new Object(), "tradeRequest");
        bindingResult.addError(new FieldError("tradeRequest", "tradeRef", "must not be blank"));
        bindingResult.addError(new FieldError("tradeRequest", "counterpartyId", "must not be null"));

        ProblemDetail problem = handler.validation(
                new MethodArgumentNotValidException(methodParameter(), bindingResult));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Validation failed");
        assertThat(problem.getDetail()).isEqualTo(
                "tradeRef: must not be blank; counterpartyId: must not be null");
    }

    @Test
    void mapsConstraintViolationToBadRequest() {
        ProblemDetail problem = handler.constraint(
                new jakarta.validation.ConstraintViolationException("id: must be positive", java.util.Set.of()));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).contains("id: must be positive");
    }

    @Test
    void mapsUnexpectedErrorsToSafeLogged500Response() {
        ProblemDetail problem = handler.handleAny(new IllegalStateException("secret database detail"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Internal server error");
        assertThat(problem.getDetail()).doesNotContain("secret database detail");
    }

    @Test
    void rendersDomainErrorsAsApplicationProblemJson() throws Exception {
        MockMvc mockMvc = standaloneSetup(new ThrowingController())
                .setControllerAdvice(handler)
                .build();

        mockMvc.perform(get("/missing").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "https://reconx.dbtraining.com/errors/trade-not-found"))
                .andExpect(jsonPath("$.title").value("Trade not found"));
    }

    @RestController
    private static class ThrowingController {
        @GetMapping("/missing")
        void missing() {
            throw new TradeNotFoundException("TR-404");
        }
    }

    private static org.springframework.core.MethodParameter methodParameter() throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", String.class);
        return new org.springframework.core.MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private static void sampleMethod(String value) {
    }
}
