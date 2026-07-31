package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.Trade;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * ============================================================================
 * TICKET-ADV056 — TradeSpecifications
 *
 * WHAT:    Static factories that return Specification<Trade> instances which
 *          callers compose with .and() / .or() in the service layer for
 *          dynamic queries.
 * HOW:     Each method returns a lambda `(root, query, cb) -> Predicate`.
 *          A null filter argument means "no constraint", which is encoded
 *          via cb.conjunction().
 * WHY:     Avoids exploding the repository with `findByXAndYAndZ...`
 *          methods for every possible combination of filters.
 * OBSERVE: GET /api/v1/trades?status=NEW&from=2026-01-01 should produce the
 *          right SQL WHERE clause — turn on `spring.jpa.show-sql` to verify.
 * ============================================================================
 *
 *  NOTE: a relation path (`root.get("counterparty").get("id")`) forces a JOIN —
 *        fine for an `equal`, but be careful with `like`.
 * ============================================================================
 */
public final class TradeSpecifications {

    private TradeSpecifications() {}

    /**
     * Inclusive date-range filter. Either bound may be omitted, leaving the
     * range open on that side; omitting both is a no-op.
     */
    public static Specification<Trade> tradeDateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return cb.conjunction();
            if (from == null)               return cb.lessThanOrEqualTo(root.get("tradeDate"), to);
            if (to == null)                 return cb.greaterThanOrEqualTo(root.get("tradeDate"), from);
            return cb.between(root.get("tradeDate"), from, to);
        };
    }

    public static Specification<Trade> hasStatus(String status) {
        return (root, query, cb) -> status == null || status.isBlank()
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<Trade> forCounterparty(Long counterpartyId) {
        return (root, query, cb) -> counterpartyId == null
                ? cb.conjunction()
                : cb.equal(root.get("counterparty").get("id"), counterpartyId);
    }

    /** Prefix match on the trade reference — {@code refLike("TRD")} matches {@code TRD-0001}. */
    public static Specification<Trade> refLike(String pattern) {
        return (root, query, cb) -> pattern == null || pattern.isBlank()
                ? cb.conjunction()
                : cb.like(root.get("tradeRef"), pattern + "%");
    }

    /**
     * Fetch-joins instrument/counterparty so TradeMapper can read
     * instrument.symbol / counterparty.name after the transaction closes
     * (open-in-view is disabled) without hitting a LazyInitializationException.
     * Skipped on the COUNT query Spring Data runs for pagination — fetch
     * joins aren't valid there.
     */
    public static Specification<Trade> withAssociations() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("instrument", JoinType.LEFT);
                root.fetch("counterparty", JoinType.LEFT);
                query.distinct(true);
            }
            return cb.conjunction();
        };
    }
}
