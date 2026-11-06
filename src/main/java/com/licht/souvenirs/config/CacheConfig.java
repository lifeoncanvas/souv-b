package com.licht.souvenirs.config;

import com.licht.souvenirs.service.CatalogService;
import com.licht.souvenirs.service.CatalogService.ProductQuery;
import com.licht.souvenirs.service.ContentService;
import jakarta.servlet.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * In-memory cache for the storefront's read-mostly data (product lists, categories, price range, site content).
 *
 * <p>Every database round trip to Neon costs ~0.3 s from a distant region, and a product list needs several, so
 * repeat requests are served from memory. Admin edits clear the affected cache at once; everything is also
 * refreshed every minute so stock levels changed by orders show up within 60 seconds (checkout always re-checks
 * stock against the database). After each refresh, and at startup, the data the shop's first page needs is loaded
 * again in the background so visitors do not wait for the database.
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true", matchIfMissing = true)
public class CacheConfig {
    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    public static final String CATALOG = "catalog";
    public static final String CONTENT = "content";

    private static final long KEEP_WARM_MS = 10 * 60_000;

    private final ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager(CATALOG, CONTENT);
    private volatile long lastRequestAt;
    private final CatalogService catalog;
    private final ContentService content;

    // @Lazy: the services are proxied by this configuration's own caching setup.
    public CacheConfig(@Lazy CatalogService catalog, @Lazy ContentService content) {
        this.catalog = catalog;
        this.content = content;
    }

    @Bean
    public CacheManager cacheManager() {
        return manager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmOnStartup() {
        warm();
    }

    /** Notes when the API was last used, so the cache is only kept warm while people are browsing. */
    @Bean
    public Filter storefrontActivityFilter() {
        return (req, res, chain) -> {
            lastRequestAt = System.currentTimeMillis();
            chain.doFilter(req, res);
        };
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void refresh() {
        manager.getCacheNames().forEach(name -> {
            var cache = manager.getCache(name);
            if (cache != null) cache.clear();
        });
        // Re-load only while there is traffic: otherwise the database is left alone so Neon can suspend it.
        if (System.currentTimeMillis() - lastRequestAt < KEEP_WARM_MS) warm();
    }

    /** Loads what the home and shop pages ask for first (the same query the storefront sends). */
    private void warm() {
        try {
            catalog.categories();
            catalog.priceRange();
            catalog.search(new ProductQuery(null, "all", null, null, null, null, "featured", 0, 12, false));
            content.all();
        } catch (RuntimeException e) {
            log.warn("Could not preload the storefront cache (database unreachable?): {}", e.getMessage());
        }
    }
}
