package com.dbtraining.reconx.repository.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * TICKET-ADV136 — Persisted DLQ message row.
 * Written by DlqConsumer when a message lands on trade-events-dlq.
 * Read/deleted by DlqAdminController on replay.
 */
@Entity
@Table(name = "dlq_messages")
public class DlqMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "trade_ref", nullable = false, length = 30)
    private String tradeRef;

    @Column(name = "original_topic", nullable = false, length = 100)
    private String originalTopic;

    @Column(name = "partition_num", nullable = false)
    private int partition;

    @Column(name = "offset_val", nullable = false)
    private long offset;

    @Column(nullable = false, length = 10000)
    private String payload;

    @Column(length = 10000)
    private String reason;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    protected DlqMessage() {}

    private DlqMessage(Builder builder) {
        this.eventId = builder.eventId;
        this.tradeRef = builder.tradeRef;
        this.originalTopic = builder.originalTopic;
        this.partition = builder.partition;
        this.offset = builder.offset;
        this.payload = builder.payload;
        this.reason = builder.reason;
        this.firstSeen = builder.firstSeen;
    }

    public static Builder builder() { return new Builder(); }

    public Long getId()              { return id; }
    public String getEventId()       { return eventId; }
    public String getTradeRef()      { return tradeRef; }
    public String getOriginalTopic() { return originalTopic; }
    public int getPartition()        { return partition; }
    public long getOffset()          { return offset; }
    public String getPayload()       { return payload; }
    public String getReason()        { return reason; }
    public Instant getFirstSeen()    { return firstSeen; }

    public static class Builder {
        private String eventId;
        private String tradeRef;
        private String originalTopic;
        private int partition;
        private long offset;
        private String payload;
        private String reason;
        private Instant firstSeen;

        public Builder eventId(String eventId)             { this.eventId = eventId; return this; }
        public Builder tradeRef(String tradeRef)           { this.tradeRef = tradeRef; return this; }
        public Builder originalTopic(String originalTopic) { this.originalTopic = originalTopic; return this; }
        public Builder partition(int partition)             { this.partition = partition; return this; }
        public Builder offset(long offset)                 { this.offset = offset; return this; }
        public Builder payload(String payload)             { this.payload = payload; return this; }
        public Builder reason(String reason)               { this.reason = reason; return this; }
        public Builder firstSeen(Instant firstSeen)        { this.firstSeen = firstSeen; return this; }

        public DlqMessage build() { return new DlqMessage(this); }
    }
}
