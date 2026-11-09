package com.researchspace.maintenance.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class MaintenanceCacheInvalidatorTest {

  @Test
  void clearsTheCacheImmediatelyAndAfterCommit() {
    CacheManager cacheManager = mock(CacheManager.class);
    Cache cache = mock(Cache.class);
    when(cacheManager.getCache(MaintenanceCacheInvalidator.CACHE_NAME)).thenReturn(cache);
    MaintenanceCacheInvalidator invalidator = new MaintenanceCacheInvalidator(cacheManager);
    MaintenanceChangedEvent event = new MaintenanceChangedEvent();

    invalidator.invalidateImmediately(event);
    invalidator.invalidateAfterCommit(event);

    verify(cache, times(2)).clear();
  }
}
