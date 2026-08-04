package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.DlqMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * TICKET-ADV136 — Repository for DLQ message persistence.
 */
public interface DlqMessageRepository extends JpaRepository<DlqMessage, Long> {
    Optional<DlqMessage> findByEventId(String eventId);
}
