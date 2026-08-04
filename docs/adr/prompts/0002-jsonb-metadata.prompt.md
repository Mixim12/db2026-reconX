You are an enterprise software architect. Write an Architecture Decision Record
(ADR) in the Michael Nygard format (Title, Status, Context, Decision,
Consequences) for the following decision.

System: ReconX, a near-prod trade reconciliation platform.
Stack: PostgreSQL 16, Spring Boot 3, Kafka, React.
Scale: ~50,000 trades/day, 5-year retention, 10 concurrent recon analysts.

Decision to record: Use a JSONB column on instruments for asset-class specific metadata (instruments.metadata).

Alternatives we considered:
1. Entity-Attribute-Value (EAV) relational model.
2. Sparse wide table with nullable columns for all asset-class attributes.
3. Subclass tables per asset class (equity_instruments, fx_instruments, etc.).

Constraints / forces:
1. Support diverse asset classes (Equities, FX, Fixed Income, Derivatives) without frequent DDL migrations.
2. Database portability constraints with local H2 unit test database skipping JSONB columns.

Format: Markdown, Nygard 5-section template, no fluff. Keep under 300 words.
Include a "Status: Accepted | Date: <YYYY-MM-DD>" line.
