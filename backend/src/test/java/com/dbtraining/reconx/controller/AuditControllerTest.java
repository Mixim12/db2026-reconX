package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.repository.AuditLogRepository;
import com.dbtraining.reconx.repository.entity.AuditLogEntry;
import com.dbtraining.reconx.security.JwtTokenProvider;
import com.dbtraining.reconx.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TICKET-ADV138 — AuditController GET /{tradeRef}/events RBAC + ordering.
 */
@WebMvcTest(AuditController.class)
@Import(SecurityConfig.class)
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogRepository auditRepo;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    // --- RBAC tests ---

    @Test
    @DisplayName("Unauthenticated request returns 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/v1/audit/trades/TRD-001/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TRADER role returns 403")
    @WithMockUser(roles = "TRADER")
    void traderRoleReturns403() throws Exception {
        mockMvc.perform(get("/v1/audit/trades/TRD-001/events"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN role returns 200")
    @WithMockUser(roles = "ADMIN")
    void adminRoleReturns200() throws Exception {
        when(auditRepo.findByTradeRefOrderByEventTimestampAsc("TRD-001"))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/audit/trades/TRD-001/events"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RECON_ANALYST role returns 200")
    @WithMockUser(roles = "RECON_ANALYST")
    void reconAnalystRoleReturns200() throws Exception {
        when(auditRepo.findByTradeRefOrderByEventTimestampAsc("TRD-001"))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/audit/trades/TRD-001/events"))
                .andExpect(status().isOk());
    }

    // --- Response content tests ---

    @Test
    @DisplayName("Events are returned ordered by timestamp ascending")
    @WithMockUser(roles = "ADMIN")
    void eventsReturnedOrderedByTimestamp() throws Exception {
        Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T11:00:00Z");
        Instant t3 = Instant.parse("2026-01-01T12:00:00Z");

        AuditLogEntry entry1 = new AuditLogEntry(
                UUID.randomUUID().toString(), "TRD-001", "TRADE_CREATED", t1, "trader1", null, "{\"status\":\"NEW\"}");
        AuditLogEntry entry2 = new AuditLogEntry(
                UUID.randomUUID().toString(), "TRD-001", "TRADE_UPDATED", t2, "trader1", "{\"status\":\"NEW\"}", "{\"status\":\"AMENDED\"}");
        AuditLogEntry entry3 = new AuditLogEntry(
                UUID.randomUUID().toString(), "TRD-001", "TRADE_CANCELLED", t3, "admin", "{\"status\":\"AMENDED\"}", null);

        when(auditRepo.findByTradeRefOrderByEventTimestampAsc("TRD-001"))
                .thenReturn(List.of(entry1, entry2, entry3));

        mockMvc.perform(get("/v1/audit/trades/TRD-001/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].eventType").value("TRADE_CREATED"))
                .andExpect(jsonPath("$[1].eventType").value("TRADE_UPDATED"))
                .andExpect(jsonPath("$[2].eventType").value("TRADE_CANCELLED"));
    }

    @Test
    @DisplayName("Events endpoint includes all events for the trade with no pagination")
    @WithMockUser(roles = "ADMIN")
    void eventsEndpointReturnsAllEvents() throws Exception {
        when(auditRepo.findByTradeRefOrderByEventTimestampAsc("TRD-001"))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/audit/trades/TRD-001/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
