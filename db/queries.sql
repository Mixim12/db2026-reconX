-- ============================================================================
-- TICKET-ADV010 — VWAP per instrument per day (window function)
-- ============================================================================
SELECT
    t.trade_ref,
    t.trade_date,
    i.symbol,
    t.quantity,
    t.price,
    (t.quantity * t.price) AS notional,
    SUM(t.price * t.quantity) OVER (PARTITION BY t.instrument_id, t.trade_date)
        / NULLIF(SUM(t.quantity) OVER (PARTITION BY t.instrument_id, t.trade_date), 0) AS vwap,
    ROW_NUMBER() OVER (PARTITION BY t.instrument_id, t.trade_date ORDER BY t.created_at) AS trade_num,
    SUM(t.quantity) OVER (PARTITION BY t.instrument_id, t.trade_date ORDER BY t.created_at ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_qty
FROM trades t
JOIN instruments i ON i.id = t.instrument_id
WHERE t.deleted_at IS NULL
  AND t.asset_class = 'EQUITY'
ORDER BY t.trade_date DESC, t.instrument_id, t.created_at;


-- ============================================================================
-- TICKET-ADV011 — Recursive CTE: trade lifecycle rollup (5 stages)
-- ============================================================================
WITH RECURSIVE trade_lifecycle AS (
    -- Base case: every trade starts as stage 1 (EXECUTION)
    SELECT
        t.id                                    AS trade_id,
        t.trade_ref,
        1                                       AS stage,
        'EXECUTION'::VARCHAR(30)                AS stage_name,
        t.created_at                            AS event_at,
        t.status::VARCHAR(30)                   AS event_status
    FROM trades t
    WHERE t.deleted_at IS NULL

    UNION ALL

    -- Recursive step: JOIN LATERAL evaluates the next stage based on current stage
    SELECT
        tl.trade_id,
        tl.trade_ref,
        tl.stage + 1                           AS stage,
        next_event.stage_name,
        next_event.event_at,
        next_event.event_status
    FROM trade_lifecycle tl
    JOIN LATERAL (
        -- Stage 1 -> Stage 2: CONFIRMATION
        SELECT
            'CONFIRMATION'::VARCHAR(30)         AS stage_name,
            tl.event_at                         AS event_at,
            'CONFIRMED'::VARCHAR(30)            AS event_status
        WHERE tl.stage = 1

        UNION ALL

        -- Stage 2 -> Stage 3: SETTLEMENT
        SELECT
            'SETTLEMENT'::VARCHAR(30)           AS stage_name,
            s.settlement_date::timestamp         AS event_at,
            s.status::VARCHAR(30)               AS event_status
        FROM settlements s
        WHERE tl.stage = 2
          AND s.trade_id = tl.trade_id

        UNION ALL

        -- Stage 3 -> Stage 4: RECON_BREAK
        SELECT
            'RECON_BREAK'::VARCHAR(30)          AS stage_name,
            rb.detected_at                      AS event_at,
            rb.status::VARCHAR(30)              AS event_status
        FROM recon_breaks rb
        WHERE tl.stage = 3
          AND rb.trade_id = tl.trade_id

        UNION ALL

        -- Stage 4 -> Stage 5: RESOLUTION
        SELECT
            'RESOLUTION'::VARCHAR(30)           AS stage_name,
            rb.resolved_at                      AS event_at,
            rb.status::VARCHAR(30)              AS event_status
        FROM recon_breaks rb
        WHERE tl.stage = 4
          AND rb.trade_id = tl.trade_id
          AND rb.resolved_at IS NOT NULL
    ) AS next_event ON TRUE
    WHERE tl.stage < 5
)
SELECT * FROM trade_lifecycle
ORDER BY trade_id, stage;


-- ============================================================================
-- ADV008 — REFRESH the daily-summary materialised view (concurrent so it can
--         run while the dashboard is reading it)
-- ============================================================================
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_recon_summary;


-- ============================================================================
-- ADV009 — JSONB lookup: which instruments have sector = 'Banking'?
-- ============================================================================
SELECT id, symbol, metadata
FROM instruments
WHERE metadata @> '{"sector":"Banking"}'::jsonb;


-- ============================================================================
-- TICKET-ADV008 — mv_daily_recon_summary: daily recon aggregate by
--                 trade_date / region / asset_class (prototyping only —
--                 the versioned copy lives in Liquibase changeset
--                 011-mv-daily-recon-summary.xml).
-- ============================================================================
SELECT
    t.trade_date,
    c.region,
    t.asset_class,
    COUNT(*)                                                        AS total_trades,
    COUNT(*) FILTER (WHERE t.status = 'MATCHED')                    AS matched_trades,
    COUNT(*) FILTER (WHERE rb.status = 'OPEN')                      AS open_breaks,
    ROUND(SUM(t.quantity * t.price)::NUMERIC, 2)                    AS gross_notional,
    ROUND(
        100.0 * COUNT(*) FILTER (WHERE t.status = 'MATCHED')
            / NULLIF(COUNT(*), 0),
        2
    )                                                                AS match_rate_pct
FROM trades t
JOIN counterparties c ON c.id = t.counterparty_id
JOIN instruments i ON i.id = t.instrument_id
LEFT JOIN recon_breaks rb ON rb.trade_id = t.id
WHERE t.deleted_at IS NULL
GROUP BY t.trade_date, c.region, t.asset_class;

-- Refresh without blocking dashboard reads (requires the unique index below):
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_recon_summary;
