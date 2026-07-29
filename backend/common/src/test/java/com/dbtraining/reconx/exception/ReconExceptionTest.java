package com.dbtraining.reconx.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ============================================================================
 * TICKET-ADV025 — Exception hierarchy
 *
 * WHAT:    Verifies the exception ladder rooted at the abstract ReconException:
 *          shape (abstract/unchecked), the four concrete subtypes, the two
 *          constructor forms, message preservation, cause chaining, package
 *          placement, and single-catch polymorphism.
 * HOW:     Reflection for the structural criteria, behavioural construct-and-
 *          throw assertions for everything observable at runtime. Parameterized
 *          over the four subtypes so a new subtype is covered by adding one row.
 * WHY:     A single `catch (ReconException)` in @RestControllerAdvice only works
 *          if every subtype really is a ReconException and never loses its
 *          message or root cause on the way up.
 * ============================================================================
 */
class ReconExceptionTest {

    private static final String PACKAGE = "com.dbtraining.reconx.exception";
    private static final String MARKER = "TR-ADV025-MARKER";

    /** The four concrete subtypes of the ladder — the parameter source for every subtype test. */
    static Stream<Class<? extends ReconException>> subtypes() {
        return Stream.of(
                InvalidTradeException.class,
                TradeNotFoundException.class,
                DuplicateTradeRefException.class,
                ReconciliationMismatchException.class);
    }

    private static ReconException newWithMessage(Class<? extends ReconException> type, String message)
            throws Exception {
        return type.getConstructor(String.class).newInstance(message);
    }

    private static ReconException newWithCause(Class<? extends ReconException> type,
                                               String message, Throwable cause) throws Exception {
        return type.getConstructor(String.class, Throwable.class).newInstance(message, cause);
    }

    // --- AC1: root is abstract and unchecked ---------------------------------

    @Test
    void reconExceptionIsAbstractAndUnchecked() {
        assertThat(Modifier.isAbstract(ReconException.class.getModifiers()))
                .as("ReconException must be abstract so nobody can throw a bare root exception")
                .isTrue();
        assertThat(RuntimeException.class.isAssignableFrom(ReconException.class))
                .as("ReconException must extend RuntimeException (unchecked)")
                .isTrue();
    }

    // --- AC2: the four concrete subtypes exist and extend the root -----------

    @ParameterizedTest(name = "{0} extends ReconException and is concrete")
    @MethodSource("subtypes")
    void subtypeExtendsReconException(Class<? extends ReconException> type) {
        assertThat(ReconException.class.isAssignableFrom(type)).isTrue();
        assertThat(type.getSuperclass()).isEqualTo(ReconException.class);
        assertThat(Modifier.isAbstract(type.getModifiers()))
                .as("%s must be concrete so it can actually be thrown", type.getSimpleName())
                .isFalse();
    }

    // --- AC3: both constructor forms exist (reflective) ----------------------

    @ParameterizedTest(name = "{0} declares (String) and (String, Throwable)")
    @MethodSource("subtypes")
    void subtypeDeclaresBothConstructors(Class<? extends ReconException> type) {
        assertThatCode(() -> type.getConstructor(String.class))
                .as("%s must expose a public (String) constructor", type.getSimpleName())
                .doesNotThrowAnyException();
        assertThatCode(() -> type.getConstructor(String.class, Throwable.class))
                .as("%s must expose a public (String, Throwable) constructor", type.getSimpleName())
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} both constructors are usable")
    @MethodSource("subtypes")
    void subtypeBothConstructorsAreUsable(Class<? extends ReconException> type) throws Exception {
        Constructor<? extends ReconException> messageOnly = type.getConstructor(String.class);
        Constructor<? extends ReconException> withCause =
                type.getConstructor(String.class, Throwable.class);

        assertThat(messageOnly.newInstance(MARKER)).isInstanceOf(type);
        assertThat(withCause.newInstance(MARKER, new IllegalStateException("root")))
                .isInstanceOf(type);
    }

    // --- AC4: chained causes must not be lost --------------------------------

    @ParameterizedTest(name = "{0} preserves the root cause instance")
    @MethodSource("subtypes")
    void subtypePreservesCause(Class<? extends ReconException> type) throws Exception {
        Throwable rootCause = new IllegalStateException("underlying failure");

        ReconException withCause = newWithCause(type, MARKER, rootCause);

        assertThat(withCause.getCause())
                .as("%s must hand the cause straight up to RuntimeException", type.getSimpleName())
                .isSameAs(rootCause);
    }

    @ParameterizedTest(name = "{0} has no cause when built from a message only")
    @MethodSource("subtypes")
    void subtypeMessageOnlyConstructorLeavesCauseNull(Class<? extends ReconException> type)
            throws Exception {
        assertThat(newWithMessage(type, MARKER).getCause()).isNull();
    }

    // --- AC5: the message is preserved by both forms -------------------------

    @ParameterizedTest(name = "{0} keeps the message in both constructor forms")
    @MethodSource("subtypes")
    void subtypePreservesMessage(Class<? extends ReconException> type) throws Exception {
        ReconException messageOnly = newWithMessage(type, MARKER);
        ReconException withCause = newWithCause(type, MARKER, new IllegalStateException("root"));

        assertThat(messageOnly.getMessage()).isNotNull().contains(MARKER);
        assertThat(withCause.getMessage()).isNotNull().contains(MARKER);
        assertThat(withCause.getMessage())
                .as("%s must decorate the message the same way in both forms", type.getSimpleName())
                .isEqualTo(messageOnly.getMessage());
    }

    // --- AC6: every ladder class lives in the exception package --------------

    @Test
    void everyLadderClassLivesInTheExceptionPackage() {
        assertThat(ReconException.class.getPackageName()).isEqualTo(PACKAGE);
        assertThat(InvalidTradeException.class.getPackageName()).isEqualTo(PACKAGE);
        assertThat(TradeNotFoundException.class.getPackageName()).isEqualTo(PACKAGE);
        assertThat(DuplicateTradeRefException.class.getPackageName()).isEqualTo(PACKAGE);
        assertThat(ReconciliationMismatchException.class.getPackageName()).isEqualTo(PACKAGE);
    }

    // --- AC7: catchable as ReconException and as RuntimeException ------------

    @ParameterizedTest(name = "{0} is catchable as ReconException and RuntimeException")
    @MethodSource("subtypes")
    void subtypeIsCatchableAsRootAndAsRuntimeException(Class<? extends ReconException> type)
            throws Exception {
        ReconException thrown = newWithMessage(type, MARKER);

        ReconException caught = null;
        try {
            throw thrown;
        } catch (ReconException e) {
            caught = e;
        }
        assertThat(caught).isSameAs(thrown);

        assertThatThrownBy(() -> { throw newWithMessage(type, MARKER); })
                .isInstanceOf(RuntimeException.class)
                .isInstanceOf(ReconException.class)
                .isInstanceOf(type);
    }

    @Test
    void aSingleCatchOfReconExceptionHandlesEverySubtype() throws Exception {
        List<Class<? extends ReconException>> all = subtypes().toList();

        for (Class<? extends ReconException> type : all) {
            ReconException caught = null;
            try {
                throw newWithCause(type, MARKER, new IllegalStateException("root"));
            } catch (ReconException e) {
                caught = e;
            }
            assertThat(caught)
                    .as("one catch (ReconException) must handle %s", type.getSimpleName())
                    .isInstanceOf(type);
        }

        assertThat(all).hasSize(4);
    }
}
