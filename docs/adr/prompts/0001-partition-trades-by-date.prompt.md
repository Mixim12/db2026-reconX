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
