# ADR-0001 — Partition the `trades` table by `trade_date`

- Status: Accepted
- Date: 2026-06-02
- Deciders: ReconX team

## Context

`trades` is our highest-volume table — ~50k inserts/day, 5-year retention = ~91M rows at steady state. The vast majority of queries (dashboards, recon runs, analyst lookups) filter by a date range (often single day or single month). A single unpartitioned table forces full-table scans for date-range deletes and complicates archival of older trade data for the 5-year-retention SLA.

## Decision

Partition `trades` by RANGE on `trade_date`, with one partition per calendar month. The primary key includes `trade_date` to satisfy Postgres' partitioning constraint. Child partitions are named `trades_yYYYYmMM` and are pre-created for the next 12 months by a monthly maintenance job.

A `trades_default` partition catches any out-of-range inserts so the table never rejects writes; the maintenance job alerts on unexpected default-partition inserts.

## Consequences

**Positive**
- Partition pruning eliminates 11/12 of the data on a typical month-filtered query.
- Archival becomes a DDL operation (`DETACH PARTITION`), not a row-level delete.
- Indexes are smaller per partition, faster to maintain.

**Negative**
- Composite PK `(id, trade_date)` complicates JPA `@Id` mapping (see ADR-0007).
- Cross-partition unique constraints (e.g., `trade_ref`) require a workaround.
- Pre-creating partitions is a recurring ops task — must be automated.

---

### Prompt Used

```text
You are an enterprise software architect. Write an Architecture Decision Record
(ADR) in the Michael Nygard format (Title, Status, Context, Decision,
Consequences) for the following decision.

System: ReconX, a near-prod trade reconciliation platform.
Stack: PostgreSQL 16, Spring Boot 3, Kafka, React.
Scale: ~50,000 trades/day, 5-year retention, 10 concurrent recon analysts.

Decision to record: Partition the trades table by RANGE on trade_date with monthly partitions.

Alternatives we considered:
1. Single unpartitioned table with B-tree index on trade_date.
2. Daily table partitioning.
3. Separate archive database with annual batch ETL migration.

Constraints / forces:
1. Postgres 16 requirement that primary key and unique constraints must include partition key.
2. 5-year retention SLA requiring fast archival and partition pruning for month-bounded queries.

Format: Markdown, Nygard 5-section template, no fluff. Keep under 300 words.
Include a "Status: Accepted | Date: <YYYY-MM-DD>" line.
```
