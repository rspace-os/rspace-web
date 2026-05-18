package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.Set;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JCacheShiroCacheTest {

  private CacheManager jCacheManager;
  private JCacheShiroCache<String, String> shiroCache;

  @BeforeEach
  void setUp() {
    CachingProvider provider = Caching.getCachingProvider();
    jCacheManager = provider.getCacheManager();
    MutableConfiguration<String, String> config = new MutableConfiguration<>();
    config.setStoreByValue(false);
    Cache<String, String> jCache = jCacheManager.createCache("test-cache", config);
    shiroCache = new JCacheShiroCache<>(jCache);
  }

  @AfterEach
  void tearDown() {
    jCacheManager.destroyCache("test-cache");
  }

  @Test
  void getReturnsNullForMissingKey() {
    assertNull(shiroCache.get("missing"));
  }

  @Test
  void putAndGet() {
    assertNull(shiroCache.put("key1", "value1"));
    assertEquals("value1", shiroCache.get("key1"));
  }

  @Test
  void putReturnsPreviousValue() {
    shiroCache.put("key1", "value1");
    String previous = shiroCache.put("key1", "value2");
    assertEquals("value1", previous);
    assertEquals("value2", shiroCache.get("key1"));
  }

  @Test
  void removeReturnsPreviousValue() {
    shiroCache.put("key1", "value1");
    String removed = shiroCache.remove("key1");
    assertEquals("value1", removed);
    assertNull(shiroCache.get("key1"));
  }

  @Test
  void removeReturnsNullForMissingKey() {
    assertNull(shiroCache.remove("missing"));
  }

  @Test
  void clearRemovesAllEntries() {
    shiroCache.put("a", "1");
    shiroCache.put("b", "2");
    shiroCache.clear();
    assertEquals(0, shiroCache.size());
    assertNull(shiroCache.get("a"));
  }

  @Test
  void sizeReflectsEntryCount() {
    assertEquals(0, shiroCache.size());
    shiroCache.put("a", "1");
    shiroCache.put("b", "2");
    assertEquals(2, shiroCache.size());
    shiroCache.remove("a");
    assertEquals(1, shiroCache.size());
  }

  @Test
  void keysReturnsAllKeys() {
    shiroCache.put("a", "1");
    shiroCache.put("b", "2");
    Set<String> keys = shiroCache.keys();
    assertEquals(Set.of("a", "b"), keys);
  }

  @Test
  void valuesReturnsAllValues() {
    shiroCache.put("a", "1");
    shiroCache.put("b", "2");
    Collection<String> values = shiroCache.values();
    assertTrue(values.contains("1"));
    assertTrue(values.contains("2"));
    assertEquals(2, values.size());
  }
}
