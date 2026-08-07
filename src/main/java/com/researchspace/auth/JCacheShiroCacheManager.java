package com.researchspace.auth;

import java.time.Duration;
import java.util.Map;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheException;
import org.apache.shiro.lang.util.Destroyable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shiro {@link org.apache.shiro.cache.CacheManager} backed by JCache (JSR-107). Each named cache is
 * created programmatically with per-cache TTL configuration matching the old ehcache-shiro.xml.
 */
public class JCacheShiroCacheManager implements org.apache.shiro.cache.CacheManager, Destroyable {

  private static final Logger log = LoggerFactory.getLogger(JCacheShiroCacheManager.class);

  private final CacheManager jCacheManager;
  private final Map<String, Duration> cacheTtls;
  private final Duration defaultTtl;

  /**
   * @param jCacheManager the JCache CacheManager to create caches in
   * @param cacheTtls per-cache TTL overrides (cache name to TTL); null duration means eternal
   * @param defaultTtl default TTL for caches not listed in cacheTtls
   */
  public JCacheShiroCacheManager(
      CacheManager jCacheManager, Map<String, Duration> cacheTtls, Duration defaultTtl) {
    this.jCacheManager = jCacheManager;
    this.cacheTtls = cacheTtls;
    this.defaultTtl = defaultTtl;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <K, V> Cache<K, V> getCache(String name) throws CacheException {
    javax.cache.Cache<K, V> jCache = (javax.cache.Cache<K, V>) jCacheManager.getCache(name);
    if (jCache == null) {
      jCache = createJCache(name);
    }
    return new JCacheShiroCache<>(jCache);
  }

  @SuppressWarnings("unchecked")
  private <K, V> javax.cache.Cache<K, V> createJCache(String name) {
    Duration ttl = cacheTtls.getOrDefault(name, defaultTtl);

    MutableConfiguration<K, V> config = new MutableConfiguration<>();
    config.setStoreByValue(false);
    if (ttl != null) {
      config.setExpiryPolicyFactory(
          CreatedExpiryPolicy.factoryOf(
              new javax.cache.expiry.Duration(
                  java.util.concurrent.TimeUnit.SECONDS, ttl.getSeconds())));
    }

    log.info("Creating Shiro JCache '{}' with TTL {}", name, ttl != null ? ttl : "eternal");
    return (javax.cache.Cache<K, V>) jCacheManager.createCache(name, config);
  }

  @Override
  public void destroy() {
    // Don't close the JCache CacheManager — it may be shared with Hibernate's second-level cache.
    // Individual Shiro caches will be GC'd when this manager is discarded.
  }
}
