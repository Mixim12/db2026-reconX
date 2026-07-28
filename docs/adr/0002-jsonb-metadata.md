# ADR-0002 — Use JSONB column for instrument metadata

- Status: Accepted
- Date: 2026-06-02
- Deciders: ReconX team

## Context

ReconX reconciles ~50,000 trades/day across diverse asset classes (Equities, FX, Fixed Income, Derivatives) for 10 concurrent analysts with a 5-year retention SLA. Each asset class requires distinct metadata attributes (e.g., dividend yield and sector for Equities; coupon rate and maturity date for Fixed Income; strike price and expiration for Derivatives). Traditional relational modeling requires either a sparse table with dozens of nullable columns, an Entity-Attribute-Value (EAV) structure, or class-per-table inheritance with heavy JOIN operations.

## Decision

Store asset-class-specific instrument metadata in a single `JSONB` column on the `instruments` table (`instruments.metadata DEFAULT '{}'::JSONB NOT NULL`). Schema validation is enforced at the Spring Boot application layer per asset class rather than through strict database table definitions.

## Consequences

**Positive**
- Flexible schemaless storage allows adding new asset classes or attributes without running DDL schema migrations.
- Avoids performance overhead of EAV JOIN queries and sparse tables with ~12+ asset-specific nullable columns.
- Efficient binary representation in Postgres 16 supports fast lookup and indexability.

**Negative**
- Postgres cannot enforce required JSON keys or data types at the schema level; integrity relies on application-layer validation.
- Dev and local test environments using H2 database lack native JSONB support, requiring Liquibase preconditions (`<dbms type="postgresql"/>`) to handle schema differences.

---

### Prompt Used

```text
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
```
