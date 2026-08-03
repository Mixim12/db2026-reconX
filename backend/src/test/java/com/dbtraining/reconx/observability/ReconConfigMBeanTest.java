package com.dbtraining.reconx.observability;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReconConfigMBeanTest {

    @Test
    void testPriceTolerance_getAndSet() {
        CacheManager cacheManager = mock(CacheManager.class);
        ReconConfigMBean mBean = new ReconConfigMBean(cacheManager);

        assertThat(mBean.getPriceTolerance()).isEqualTo(0.01);

        mBean.setPriceTolerance(0.05);
        assertThat(mBean.getPriceTolerance()).isEqualTo(0.05);

        assertThatThrownBy(() -> mBean.setPriceTolerance(-0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tolerance must be 0..1");

        assertThatThrownBy(() -> mBean.setPriceTolerance(1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tolerance must be 0..1");
    }

    @Test
    void testCachingEnabled_getAndSet() {
        CacheManager cacheManager = mock(CacheManager.class);
        ReconConfigMBean mBean = new ReconConfigMBean(cacheManager);

        assertThat(mBean.isCachingEnabled()).isTrue();

        mBean.setCachingEnabled(false);
        assertThat(mBean.isCachingEnabled()).isFalse();
    }

    @Test
    void testClearCache_clearsAllRegisteredCaches() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache1 = mock(Cache.class);
        Cache cache2 = mock(Cache.class);

        when(cacheManager.getCacheNames()).thenReturn(List.of("instruments", "counterparties"));
        when(cacheManager.getCache("instruments")).thenReturn(cache1);
        when(cacheManager.getCache("counterparties")).thenReturn(cache2);

        ReconConfigMBean mBean = new ReconConfigMBean(cacheManager);
        mBean.clearCache();

        verify(cache1).clear();
        verify(cache2).clear();
    }
}
