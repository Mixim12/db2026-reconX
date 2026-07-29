package com.dbtraining.reconx.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ============================================================================
 * TICKET-ADV024 — Tests for the immutable value object: TradeRef
 *
 * WHAT:    Covers value-based equality and the compact-constructor validation
 *          of the platform reference format AAA-YYYYMMDD-NNNN, including the
 *          near-miss cases that prove the regex anchors are present.
 * WHY:     TradeRef is the natural key ADV028's equals/hashCode hashes on; a
 *          loose regex would let malformed keys into the reconciliation set.
 * ============================================================================
 */
class TradeRefTest {

    private static final String VALID = "EQU-20260602-0001";

    // --- AC1: records with value-based equality -----------------------------

    @Test
    @DisplayName("AC1: two TradeRef with the same value are equal")
    void equalsIsValueBased() {
        TradeRef a = TradeRef.of(VALID);
        TradeRef b = TradeRef.of(VALID);

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotSameAs(b);
    }

    @Test
    @DisplayName("AC1: equal TradeRef instances share a hashCode")
    void hashCodeIsValueBased() {
        assertThat(TradeRef.of(VALID).hashCode()).isEqualTo(TradeRef.of(VALID).hashCode());
    }

    @Test
    @DisplayName("AC1: TradeRef is a record, so it exposes no setters")
    void tradeRefIsARecord() {
        assertThat(TradeRef.class.isRecord()).isTrue();
        assertThat(TradeRef.class.getMethods())
                .noneMatch(m -> m.getName().startsWith("set"));
    }

    @Test
    @DisplayName("AC1: different reference values are not equal")
    void differentValuesAreNotEqual() {
        assertThat(TradeRef.of(VALID)).isNotEqualTo(TradeRef.of("FIX-20260602-0001"));
    }

    // --- AC7: valid format accepted, junk rejected --------------------------

    @Test
    @DisplayName("AC7: a well-formed reference is accepted and exposed verbatim")
    void acceptsWellFormedReference() {
        TradeRef ref = TradeRef.of(VALID);

        assertThat(ref.value()).isEqualTo(VALID);
        assertThat(ref).hasToString(VALID);
    }

    @ParameterizedTest(name = "accepts {0}")
    @ValueSource(strings = {"EQU-20260602-0001", "FIX-19991231-9999", "ABC-00000000-0000"})
    @DisplayName("AC7: any AAA-YYYYMMDD-NNNN shaped reference is accepted")
    void acceptsAllWellFormedShapes(String candidate) {
        assertThat(TradeRef.of(candidate).value()).isEqualTo(candidate);
    }

    @Test
    @DisplayName("AC7: 'foo' is rejected with a message naming AAA-YYYYMMDD-NNNN")
    void rejectsFooNamingTheExpectedFormat() {
        assertThatThrownBy(() -> TradeRef.of("foo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AAA-YYYYMMDD-NNNN")
                .hasMessageContaining("foo");
    }

    @Test
    @DisplayName("AC7: null is rejected with NullPointerException")
    void rejectsNull() {
        assertThatThrownBy(() -> TradeRef.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- AC8: near-miss rejections prove the anchors ------------------------

    @ParameterizedTest(name = "rejects ''{0}''")
    @ValueSource(strings = {
            "equ-20260602-0001",   // lowercase prefix
            "EQ-20260602-0001",    // two-letter prefix
            "EQUI-20260602-0001",  // four-letter prefix
            "EQU-2026060-0001",    // seven-digit date
            "EQU-202606020-0001",  // nine-digit date
            "EQU-20260602-001",    // three-digit sequence
            "EQU-20260602-00001",  // five-digit sequence
            "XEQU-20260602-0001",  // leading junk
            "EQU-20260602-0001X",  // trailing junk
            " EQU-20260602-0001",  // leading whitespace
            "EQU-20260602-0001 ",  // trailing whitespace
            "EQU20260602-0001",    // missing first dash
            "EQU-20260602_0001",   // wrong separator
            "EQU-2026O602-0001",   // letter smuggled into the date
            ""                     // empty string
    })
    @DisplayName("AC8: near-miss references are rejected with the format message")
    void rejectsNearMisses(String candidate) {
        assertThatThrownBy(() -> TradeRef.of(candidate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AAA-YYYYMMDD-NNNN");
    }

    @Test
    @DisplayName("AC8: a multi-line value cannot slip past the anchors")
    void rejectsMultilineValue() {
        assertThatThrownBy(() -> TradeRef.of(VALID + "\nEQU-20260602-0002"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
