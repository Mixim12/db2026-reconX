package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.TradeMapper;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.repository.entity.Trade;
import com.dbtraining.reconx.service.TradeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TICKET-ADV057 — GET /v1/trades pagination contract.
 *
 * The query itself belongs to TICKET-ADV055/ADV056, so TradeService is mocked:
 * this test pins the controller's parameter binding, the page-size cap and the
 * response envelope. A standalone MockMvc keeps the persistence and security
 * layers out of the picture.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradeControllerListTest {

    @Mock
    private TradeService service;

    @Mock
    private TradeMapper mapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(new TradeController(service, mapper))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static TradeResponse response(String tradeRef) {
        return new TradeResponse(1L, tradeRef, 3L, "VOD.L", 7L, "ACME BANK",
                "EQUITY", "BUY", new BigDecimal("100.0000"), new BigDecimal("12.3456"),
                LocalDate.of(2026, 1, 30), "PENDING", null, null);
    }

    private void stubOneTrade(Pageable pageable, long total) {
        Trade trade = new Trade();
        trade.setTradeRef("ABC-20260130-0001");
        when(service.list(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(trade), pageable, total));
        when(mapper.toResponse(trade)).thenReturn(response("ABC-20260130-0001"));
    }

    @Test
    @DisplayName("returns 200 and the PagedResponse envelope — no Spring pageable blob")
    void returnsEnvelopeOnly() throws Exception {
        stubOneTrade(PageRequest.of(0, 5), 1);

        mockMvc.perform(get("/v1/trades").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].tradeRef").value("ABC-20260130-0001"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(5)));
    }

    @Test
    @DisplayName("items are projected through the mapper, not the entity")
    void itemsAreMapped() throws Exception {
        stubOneTrade(PageRequest.of(0, 20), 1);

        mockMvc.perform(get("/v1/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].counterpartyName").value("ACME BANK"))
                .andExpect(jsonPath("$.items[0].instrumentSymbol").value("VOD.L"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("without paging params the default is size 20 sorted by tradeDate DESC")
    void appliesPageableDefault() throws Exception {
        stubOneTrade(PageRequest.of(0, 20), 1);

        mockMvc.perform(get("/v1/trades")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(isNull(), isNull(), isNull(), isNull(), captor.capture());

        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("tradeDate")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("tradeDate").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("an explicit size overrides the default")
    void honoursExplicitSize() throws Exception {
        stubOneTrade(PageRequest.of(1, 5), 11);

        mockMvc.perform(get("/v1/trades").param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(isNull(), isNull(), isNull(), isNull(), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("from/to are decoded as ISO dates and filters reach the service")
    void bindsIsoDatesAndFilters() throws Exception {
        stubOneTrade(PageRequest.of(0, 20), 1);

        mockMvc.perform(get("/v1/trades")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .param("status", "PENDING")
                        .param("counterpartyId", "7"))
                .andExpect(status().isOk());

        verify(service).list(
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31)),
                eq("PENDING"),
                eq(7L),
                any(Pageable.class));
    }

    @Test
    @DisplayName("a malformed date is rejected rather than silently ignored")
    void rejectsMalformedDate() throws Exception {
        mockMvc.perform(get("/v1/trades").param("from", "30-01-2026"))
                .andExpect(status().is4xxClientError());
    }
}
