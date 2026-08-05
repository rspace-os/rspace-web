package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import org.apache.shiro.cache.Cache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JCacheShiroCacheManagerTest {

  private CacheManager jCacheManager;
  private JCacheShiroCacheManager shiroCacheManager;

  @BeforeEach
  void setUp() {
    CachingProvider provider = Caching.getCachingProvider();
    jCacheManager = provider.getCacheManager();

    Map<String, Duration> ttls = new HashMap<>();
    ttls.put("eternal-cache", null);
    ttls.put("short-ttl-cache", Duration.ofSeconds(1));

    shiroCacheManager = new JCacheShiroCacheManager(jCacheManager, ttls, Duration.ofSeconds(300));
  }

  @AfterEach
  void tearDown() {
    for (String name : jCacheManager.getCacheNames()) {
      jCacheManager.destroyCache(name);
    }
  }

  @Test
  void getCacheCreatesNewCache() {
    Cache<String, String> cache = shiroCacheManager.getCache("eternal-cache");
    assertNotNull(cache);
  }

  @Test
  void getCacheReturnsFunctionalCache() {
    Cache<String, String> cache = shiroCacheManager.getCache("eternal-cache");
    cache.put("key", "value");
    assertEquals("value", cache.get("key"));
  }

  @Test
  void getCacheCreatesUnknownCacheWithDefaultTtl() {
    Cache<String, String> cache = shiroCacheManager.getCache("unknown-cache");
    assertNotNull(cache);
    cache.put("key", "value");
    assertEquals("value", cache.get("key"));
  }

  @Test
  void eternalCacheDoesNotExpire() throws InterruptedException {
    Cache<String, String> cache = shiroCacheManager.getCache("eternal-cache");
    cache.put("key", "value");
    Thread.sleep(50);
    assertEquals("value", cache.get("key"));
  }

  @Test
  void shortTtlCacheExpires() throws InterruptedException {
    Cache<String, String> cache = shiroCacheManager.getCache("short-ttl-cache");
    cache.put("key", "value");
    assertEquals("value", cache.get("key"));
    Thread.sleep(1500);
    assertNull(cache.get("key"));
  }

  @Test
  void multipleCachesAreIndependent() {
    Cache<String, String> cache1 = shiroCacheManager.getCache("eternal-cache");
    Cache<String, String> cache2 = shiroCacheManager.getCache("short-ttl-cache");
    cache1.put("key", "from-cache1");
    cache2.put("key", "from-cache2");
    assertEquals("from-cache1", cache1.get("key"));
    assertEquals("from-cache2", cache2.get("key"));
  }

  @Test
  void getCacheTwiceReturnsCacheBackedBySameData() {
    Cache<String, String> first = shiroCacheManager.getCache("eternal-cache");
    first.put("key", "value");
    Cache<String, String> second = shiroCacheManager.getCache("eternal-cache");
    assertEquals("value", second.get("key"));
  }
}
