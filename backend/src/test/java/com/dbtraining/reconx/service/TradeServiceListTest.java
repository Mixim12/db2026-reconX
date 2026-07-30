package com.dbtraining.reconx.service;

import com.dbtraining.reconx.kafka.TradeEventProducer;
import com.dbtraining.reconx.observability.TradeMetrics;
import com.dbtraining.reconx.repository.CounterpartyRepository;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.repository.TradeRepository;
import com.dbtraining.reconx.repository.entity.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TICKET-ADV056 — the query service is the composition site: it chains the
 * TradeSpecifications factories and hands the result to
 * tradeRepository.findAll(spec, pageable).
 */
@ExtendWith(MockitoExtension.class)
class TradeServiceListTest {

    @Mock private TradeRepository tradeRepo;
    @Mock private CounterpartyRepository cpRepo;
    @Mock private InstrumentRepository instRepo;
    @Mock private TradeEventProducer events;
    @Mock private TradeMetrics metrics;

    @InjectMocks private TradeService service;

    @Test
    @DisplayName("list composes a Specification and delegates to findAll(spec, pageable)")
    void listDelegatesToSpecificationExecutor() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Trade> expected = new PageImpl<>(List.of(new Trade()));
        when(tradeRepo.findAll(any(Specification.class), eq(pageable))).thenReturn(expected);

        Page<Trade> actual = service.list(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "SETTLED", 7L, pageable);

        assertThat(actual).isSameAs(expected);

        ArgumentCaptor<Specification<Trade>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(tradeRepo).findAll(spec.capture(), eq(pageable));
        assertThat(spec.getValue()).isNotNull();
    }

    @Test
    @DisplayName("list still builds a Specification when every filter is null")
    void listHandlesAllNullFilters() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Trade> expected = new PageImpl<>(List.of());
        when(tradeRepo.findAll(any(Specification.class), eq(pageable))).thenReturn(expected);

        Page<Trade> actual = service.list(null, null, null, null, pageable);

        assertThat(actual).isSameAs(expected);
        verify(tradeRepo).findAll(any(Specification.class), eq(pageable));
    }
}
