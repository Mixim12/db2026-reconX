package com.dbtraining.reconx.repository.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * TICKET-ADV051 — JPA entity Instrument. The metadata column is mapped with
 * Hibernate's native JSON type, which each dialect renders natively: `jsonb`
 * on Postgres (matching the Liquibase changelog) and `json` on H2.
 */
@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "asset_class", nullable = false, length = 20)
    private String assetClass;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 12)
    private String isin;

    /**
     * JSON metadata: tick size, lot size, exchange code, etc.
     * Stored as JSONB on Postgres (queryable via the @> operator) and as the
     * H2 JSON type on dev/test, so no hardcoded columnDefinition is needed.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata = new HashMap<>();

    public Instrument() {}

    public Long getId()                  { return id; }
    public String getSymbol()            { return symbol; }
    public String getName()              { return name; }
    public String getAssetClass()        { return assetClass; }
    public String getCurrency()          { return currency; }
    public String getIsin()              { return isin; }
    public Map<String, Object> getMetadata() { return metadata; }

    public void setId(Long id)                        { this.id = id; }
    public void setSymbol(String symbol)              { this.symbol = symbol; }
    public void setName(String name)                  { this.name = name; }
    public void setAssetClass(String assetClass)      { this.assetClass = assetClass; }
    public void setCurrency(String currency)          { this.currency = currency; }
    public void setIsin(String isin)                  { this.isin = isin; }
    public void setMetadata(Map<String, Object> meta) { this.metadata = meta != null ? meta : new HashMap<>(); }
}
