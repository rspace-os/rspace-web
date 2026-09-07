package com.researchspace.maintenance.service;

import static java.util.Objects.requireNonNull;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Keeps the derived next-maintenance cache coherent with maintenance writes. */
@Component
final class MaintenanceCacheInvalidator {

  static final String CACHE_NAME = "com.researchspace.maintenance.service.impl.Maintenance";

  private final Cache cache;

  MaintenanceCacheInvalidator(CacheManager cacheManager) {
    cache = requireNonNull(cacheManager.getCache(CACHE_NAME), "Missing maintenance cache");
  }

  @EventListener
  void invalidateImmediately(MaintenanceChangedEvent ignored) {
    cache.clear();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void invalidateAfterCommit(MaintenanceChangedEvent ignored) {
    cache.clear();
  }
}

record MaintenanceChangedEvent() {}
