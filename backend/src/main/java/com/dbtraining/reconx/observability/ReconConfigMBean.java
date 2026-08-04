package com.dbtraining.reconx.observability;

import org.springframework.cache.CacheManager;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;


@Component
@ManagedResource(
    objectName = "reconx:type=ReconConfig",
    description = "Runtime tuning for the reconciliation engine"
)
public class ReconConfigMBean {

    private volatile double priceTolerance = 0.01;
    private volatile boolean cachingEnabled = true;
    private final CacheManager cacheManager;

    public ReconConfigMBean(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @ManagedAttribute(description = "Price tolerance for break detection (0.0 - 1.0)")
    public double getPriceTolerance() {
        return priceTolerance;
    }

    @ManagedAttribute
    public void setPriceTolerance(double v) {
        if (v < 0 || v > 1) {
            throw new IllegalArgumentException("tolerance must be 0..1");
        }
        this.priceTolerance = v;
    }

    @ManagedAttribute(description = "Enable or disable reference data caching")
    public boolean isCachingEnabled() {
        return cachingEnabled;
    }

    @ManagedAttribute
    public void setCachingEnabled(boolean enabled) {
        this.cachingEnabled = enabled;
    }

    @ManagedOperation(description = "Evict all entries from all registered caches")
    public void clearCache() {
        cacheManager.getCacheNames().forEach(n -> {
            var cache = cacheManager.getCache(n);
            if (cache != null) {
                cache.clear();
            }
        });
    }
}
