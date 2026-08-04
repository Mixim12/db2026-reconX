package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * ============================================================================
 * TICKET-ADV055 — Custom JPQL filter query
 * TICKET-ADV056 — Specification-based dynamic queries (JpaSpecificationExecutor)
 * TICKET-ADV057 — Pageable / Page<T> for paginated list endpoints
 *
 * WHAT:    Data-access surface for trades — a derived single-field lookup plus
 *          a JPQL query with an optional status and counterparty filter.
 * HOW:     findByTradeRef is derived from the method name; findByFilters uses
 *          the (:param IS NULL OR field = :param) idiom so a single query
 *          serves every combination of supplied filters.
 * WHY:     The list endpoint (ADV057/ADV063) and the Kafka dedupe lookup both
 *          read through here. JpaSpecificationExecutor is what lets the
 *          service layer compose TradeSpecifications (ADV056) instead of
 *          growing a findByXAndYAndZ method per filter combination.
 * OBSERVE: With `spring.jpa.show-sql`, findByFilters renders a WHERE clause
 *          containing `trade_date BETWEEN ? AND ? AND (? IS NULL OR status = ?)`.
 * ============================================================================
 */
public interface TradeRepository
        extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {

    /**
     * The read paths hand entities back to the controller layer, which maps them
     * to DTOs after the service transaction has closed. With
     * spring.jpa.open-in-view=false (ADV049) and both @ManyToOne associations
     * LAZY (ADV050), touching trade.instrument or trade.counterparty during that
     * mapping raises LazyInitializationException — so the associations are
     * fetched up front here. It also collapses the list endpoint's N+1.
     */
    @Override
    @EntityGraph(attributePaths = {"instrument", "counterparty"})
    Optional<Trade> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"instrument", "counterparty"})
    Page<Trade> findAll(Specification<Trade> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"instrument", "counterparty"})
    Optional<Trade> findByTradeRef(String tradeRef);

    /**
     * Fetch-joins instrument/counterparty so callers that map straight to
     * TradeResponse after the transaction closes (open-in-view is disabled)
     * don't hit a LazyInitializationException — see updateStatus().
     */
    @Query("SELECT t FROM Trade t JOIN FETCH t.instrument JOIN FETCH t.counterparty WHERE t.id = :id")
    Optional<Trade> findByIdWithAssociations(@Param("id") Long id);

    @EntityGraph(attributePaths = {"instrument", "counterparty"})
    @Query("""
        SELECT t FROM Trade t
        WHERE (:from IS NULL OR t.tradeDate >= :from)
          AND (:to IS NULL OR t.tradeDate <= :to)
          AND (:status IS NULL OR t.status = :status)
          AND (:counterpartyId IS NULL OR t.counterparty.id = :counterpartyId)
        """)
    Page<Trade> findByFilters(@Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              @Param("status") String status,
                              @Param("counterpartyId") Long counterpartyId,
                              Pageable pageable);

    long countByStatus(String status);
}
