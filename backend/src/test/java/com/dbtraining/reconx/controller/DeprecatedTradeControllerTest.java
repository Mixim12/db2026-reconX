package com.dbtraining.reconx.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ============================================================================
 * TICKET-ADV080 — Deprecated endpoint test
 *
 * Verifies that the deprecated /v0/trades endpoint returns 410 Gone with
 * the three standard deprecation headers: Deprecation, Sunset, and Link.
 * Uses standalone MockMvc (no Spring context) to match existing test patterns.
 * ============================================================================
 */
class DeprecatedTradeControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DeprecatedTradeController())
                .build();
    }

    @Test
    @DisplayName("GET /v0/trades returns 410 Gone")
    void deprecated_v0_trades_returns_410_gone() throws Exception {
        mockMvc.perform(get("/v0/trades"))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("GET /v0/trades sets Deprecation: true header")
    void deprecated_v0_trades_sets_deprecation_header() throws Exception {
        mockMvc.perform(get("/v0/trades"))
                .andExpect(header().string("Deprecation", "true"));
    }

    @Test
    @DisplayName("GET /v0/trades sets Sunset HTTP-date header")
    void deprecated_v0_trades_sets_sunset_header() throws Exception {
        mockMvc.perform(get("/v0/trades"))
                .andExpect(header().exists("Sunset"));
    }

    @Test
    @DisplayName("GET /v0/trades sets Link header pointing to v1 successor")
    void deprecated_v0_trades_sets_link_header_to_successor() throws Exception {
        mockMvc.perform(get("/v0/trades"))
                .andExpect(header().string("Link",
                        "</api/v1/trades>; rel=\"successor-version\""));
    }
}
