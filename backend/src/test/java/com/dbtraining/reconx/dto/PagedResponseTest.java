package com.dbtraining.reconx.dto;

import com.dbtraining.reconx.repository.entity.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV053 — PagedResponse<T> envelope.
 */
class PagedResponseTest {

    private static Trade trade(String ref) {
        Trade t = new Trade();
        t.setTradeRef(ref);
        return t;
    }

    @Test
    @DisplayName("of() maps page content through the supplied function")
    void ofMapsContent() {
        Page<Trade> page = new PageImpl<>(
                List.of(trade("ref1"), trade("ref2")),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "tradeDate")),
                2);

        PagedResponse<String> response = PagedResponse.of(page, Trade::getTradeRef);

        assertThat(response.items()).containsExactly("ref1", "ref2");
        assertThat(response.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("of() copies page number, size and total pages off the Page")
    void ofCopiesPageMetadata() {
        Page<Trade> page = new PageImpl<>(
                List.of(trade("ref1")),
                PageRequest.of(2, 5),
                11);

        PagedResponse<String> response = PagedResponse.of(page, Trade::getTradeRef);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(11);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("of() on an empty page yields an empty item list, not null")
    void ofEmptyPage() {
        PagedResponse<String> response =
                PagedResponse.of(Page.empty(PageRequest.of(0, 20)), Trade::getTradeRef);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    @DisplayName("record exposes exactly items, page, size, totalElements, totalPages")
    void recordComponents() {
        assertThat(PagedResponse.class.isRecord()).isTrue();
        assertThat(PagedResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("items", "page", "size", "totalElements", "totalPages");
    }
}
