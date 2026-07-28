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
