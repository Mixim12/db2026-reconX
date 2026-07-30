package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.Instrument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class InstrumentMetadataTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Persist instrument with jsonb metadata, reload, and verify equality")
    void testSaveAndReloadInstrumentMetadata() {
        Instrument instrument = new Instrument();
        instrument.setSymbol("AAPL");
        instrument.setName("Apple Inc.");
        instrument.setAssetClass("EQUITY");
        instrument.setCurrency("USD");
        instrument.setIsin("US0378331005");
        instrument.setMetadata(Map.of("isin", "US0378331005", "sector", "Technology"));

        Instrument saved = entityManager.persistAndFlush(instrument);
        entityManager.clear();

        Instrument reloaded = entityManager.find(Instrument.class, saved.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getSymbol()).isEqualTo("AAPL");
        assertThat(reloaded.getMetadata())
                .containsEntry("isin", "US0378331005")
                .containsEntry("sector", "Technology");
    }
}
