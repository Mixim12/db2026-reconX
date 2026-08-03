package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.kafka.TradeEventProducer;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.dbtraining.reconx.repository.entity.DlqMessage;
import com.dbtraining.reconx.security.JwtTokenProvider;
import com.dbtraining.reconx.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TICKET-ADV136 — DlqAdminController RBAC + replay behaviour.
 */
@WebMvcTest(DlqAdminController.class)
@Import(SecurityConfig.class)
class DlqAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DlqMessageRepository repo;

    @MockBean
    private TradeEventProducer producer;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private DlqMessage sampleDlqMessage(UUID eventId) {
        return DlqMessage.builder()
                .eventId(eventId.toString())
                .tradeRef("EQU-001")
                .originalTopic("trade-events")
                .partition(0)
                .offset(42L)
                .payload("{\"eventId\":\"" + eventId + "\",\"tradeRef\":\"EQU-001\",\"eventType\":\"TRADE_CREATED\"}")
                .reason("test error")
                .firstSeen(Instant.now())
                .build();
    }

    // --- RBAC tests ---

    @Test
    @DisplayName("Unauthenticated request returns 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/v1/admin/dlq/replay")
                        .param("eventId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TRADER role returns 403")
    @WithMockUser(roles = "TRADER")
    void traderRoleReturns403() throws Exception {
        mockMvc.perform(post("/v1/admin/dlq/replay")
                        .param("eventId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VIEWER role returns 403")
    @WithMockUser(roles = "VIEWER")
    void viewerRoleReturns403() throws Exception {
        mockMvc.perform(post("/v1/admin/dlq/replay")
                        .param("eventId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    // --- Dry run tests ---

    @Test
    @DisplayName("ADMIN dry-run returns preview without replaying")
    @WithMockUser(roles = "ADMIN")
    void adminDryRunReturnsPreview() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(repo.findByEventId(eventId.toString())).thenReturn(Optional.of(sampleDlqMessage(eventId)));

        mockMvc.perform(post("/v1/admin/dlq/replay")
                        .param("eventId", eventId.toString())
                        .param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.wouldReplayTo").value("trade-events"))
                .andExpect(jsonPath("$.tradeRef").value("EQU-001"));

        verify(producer, never()).publish(any());
        verify(repo, never()).delete(any(DlqMessage.class));
    }

    // --- Real replay tests ---

    @Test
    @DisplayName("ADMIN real replay publishes and deletes DLQ row")
    @WithMockUser(roles = "ADMIN")
    void adminRealReplayPublishesAndDeletes() throws Exception {
        UUID eventId = UUID.randomUUID();
        DlqMessage msg = sampleDlqMessage(eventId);
        when(repo.findByEventId(eventId.toString())).thenReturn(Optional.of(msg));

        mockMvc.perform(post("/v1/admin/dlq/replay")
                        .param("eventId", eventId.toString())
                        .param("dryRun", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.topic").value("trade-events"));

        verify(producer).publish(any());
        verify(repo).delete(msg);
    }

    @Test
    @DisplayName("ADMIN replay defaults dryRun=false when not specified")
    @WithMockUser(roles = "ADMIN")
    void adminReplayDefaultsDryRunFalse() throws Exception {
        UUID eventId = UUID.randomUUID();
        DlqMessage msg = sampleDlqMessage(eventId);
        when(repo.findByEventId(eventId.toString())).thenReturn(Optional.of(msg));

        mockMvc.perform(post("/v1/admin/dlq/replay")
                        .param("eventId", eventId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));

        verify(producer).publish(any());
    }

    // --- Error cases ---

    @Test
    @DisplayName("ADMIN replay with unknown eventId returns 404")
    @WithMockUser(roles = "ADMIN")
    void unknownEventIdReturns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(repo.findByEventId(eventId.toString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/v1/admin/dlq/replay")
                        .param("eventId", eventId.toString()))
                .andExpect(status().isNotFound());
    }
}
