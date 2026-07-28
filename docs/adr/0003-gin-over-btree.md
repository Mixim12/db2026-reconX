# ADR-0003 — Use GIN jsonb_path_ops index over B-tree for instrument metadata

- Status: Accepted
- Date: 2026-06-02
- Deciders: ReconX team

## Context

Recon analysts and automated reconciliation runs query `instruments` by dynamic JSON metadata fields (e.g., `sector`, `country`, `issuer`). With ~50,000 trades/day referencing instruments across multiple asset classes and a 5-year retention SLA (~91M rows total trade scale), unindexed JSON queries cause full table scans. Standard B-tree indexes cannot index arbitrary, unstructured JSON keys.

## Decision

Index the `instruments.metadata` column using a GIN (Generalized Inverted Index) with the `jsonb_path_ops` operator class (`CREATE INDEX idx_instruments_metadata_gin ON instruments USING GIN (metadata jsonb_path_ops)`).

## Consequences

**Positive**
- `jsonb_path_ops` produces an index up to 40% smaller than standard GIN (`jsonb_ops`) because it hashes path-value pairs rather than indexing individual keys and values separately.
- Enables sub-millisecond lookup for JSON containment queries (`metadata @> '{"sector": "Technology"}'`).
- Single index covers all present and future metadata fields without creating individual B-tree functional indexes.

**Negative**
- `jsonb_path_ops` only supports containment queries (`@>`) and does not support key-existence operators (`?`, `?|`, `?&`).
- GIN indexes increase write overhead and disk I/O on instrument INSERT and UPDATE operations compared to unindexed columns.

---

### Prompt Used

```text
You are an enterprise software architect. Write an Architecture Decision Record
(ADR) in the Michael Nygard format (Title, Status, Context, Decision,
Consequences) for the following decision.

System: ReconX, a near-prod trade reconciliation platform.
Stack: PostgreSQL 16, Spring Boot 3, Kafka, React.
Scale: ~50,000 trades/day, 5-year retention, 10 concurrent recon analysts.

Decision to record: Use GIN index with jsonb_path_ops over B-tree for instruments.metadata.

Alternatives we considered:
1. Standard B-tree functional indexes on specific JSON paths (e.g. metadata->>'sector').
2. Standard GIN index using default jsonb_ops operator class.
3. Unindexed JSONB with relational normalized lookup tables.

Constraints / forces:
1. Need fast containment queries (@>) for analyst dashboards across arbitrary JSON keys.
2. Minimize index storage footprint and write overhead in Postgres 16.

Format: Markdown, Nygard 5-section template, no fluff. Keep under 300 words.
Include a "Status: Accepted | Date: <YYYY-MM-DD>" line.
```
