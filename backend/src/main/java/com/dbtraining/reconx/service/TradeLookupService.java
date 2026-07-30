package com.dbtraining.reconx.service;

import com.dbtraining.reconx.repository.CounterpartyRepository;
import com.dbtraining.reconx.repository.TradeRepository;
import com.dbtraining.reconx.repository.entity.Counterparty;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * ============================================================================
 * TICKET-ADV039 — Optional chaining for null-safe lookups
 *
 * WHAT:    Resolves a trade reference to its counterparty via a single
 *          {@code Optional} chain — no {@code isPresent()}, no {@code .get()},
 *          no null checks.
 * HOW:     {@code findByTradeRef → map(counterparty id) → flatMap(findById)
 *          → orElseThrow}.
 * WHY:     This pattern is reused by Day 4's controllers for 404 handling and
 *          keeps service code free of nested null-check boilerplate.
 * OBSERVE: {@code grep -E 'isPresent|\.get\(\)'} over this file returns zero
 *          hits; a missing trade ref throws {@link NoSuchElementException}
 *          with the ref embedded in the message.
 * ============================================================================
 */
@Service
public class TradeLookupService {

    private final TradeRepository tradeRepo;
    private final CounterpartyRepository cpRepo;

    public TradeLookupService(TradeRepository tradeRepo, CounterpartyRepository cpRepo) {
        this.tradeRepo = tradeRepo;
        this.cpRepo = cpRepo;
    }

    /**
     * Walk from trade reference → counterparty via Optional chaining.
     *
     * @param tradeRef the unique trade reference string
     * @return the resolved {@link Counterparty}
     * @throws NoSuchElementException if the trade ref is unknown or has no
     *                                resolvable counterparty
     */
    public Counterparty counterpartyForTradeRef(String tradeRef) {
        return tradeRepo.findByTradeRef(tradeRef)
                .map(trade -> trade.getCounterparty().getId())
                .flatMap(cpRepo::findById)
                .orElseThrow(() -> new NoSuchElementException(
                        "No counterparty resolvable for trade " + tradeRef));
    }
}
