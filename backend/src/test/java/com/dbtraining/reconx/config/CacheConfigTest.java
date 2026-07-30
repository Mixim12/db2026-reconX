package com.dbtraining.reconx.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    @Test
    void testCacheManager_configuresInstrumentsAndCounterpartiesCaches() {
        CacheConfig config = new CacheConfig();
        CacheManager cacheManager = config.cacheManager();

        Cache instruments = cacheManager.getCache("instruments");
        Cache counterparties = cacheManager.getCache("counterparties");

        assertThat(instruments).isNotNull().isInstanceOf(CaffeineCache.class);
        assertThat(counterparties).isNotNull().isInstanceOf(CaffeineCache.class);

        CaffeineCache caffeineInstruments = (CaffeineCache) instruments;
        CaffeineCache caffeineCounterparties = (CaffeineCache) counterparties;

        assertThat(caffeineInstruments.getNativeCache().stats()).isNotNull();
        assertThat(caffeineCounterparties.getNativeCache().stats()).isNotNull();
    }
}
