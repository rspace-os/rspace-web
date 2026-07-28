package com.researchspace.core.util.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TimeLimitedMemoryCacheTest {

  String license;
  TimeLimitedMemoryCache<String> cache;

  @BeforeEach
  public void setUp() throws Exception {
    license = "anything";
    cache = new DefaultTimeLimitedMemoryCache<>();
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testCache() throws InterruptedException {
    assertNull(cache.getCachedItem());
    assertTrue(cache.isEmpty());
    // Olicense.setUniqueKey("a");
    cache.cache(license);
    assertTrue(cache.isValid());
    assertNotNull(cache.getCachedItem());
    assertEquals(license, cache.getCachedItem());

    cache.clear();
    assertNull(cache.getCachedItem());

    cache.cache(license);
    cache.setCacheTimeMillis(1L);
    // shortest cache time
    Thread.sleep(10);
    assertTrue(cache.isStale());

    cache.setCacheTimeMillis(10000L);
    assertTrue(cache.isValid());
  }
}
