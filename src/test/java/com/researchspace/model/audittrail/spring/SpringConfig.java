package com.researchspace.model.audittrail.spring;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.service.audit.search.LogLineContentProvider;
import com.researchspace.service.audit.search.LogLineContentProviderImpl;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Guarded by a dedicated profile: this class sits in a package that the application contexts
 * component-scan, and without the guard its cacheManager bean replaces the application's
 * JCache-backed one in every Spring test context.
 */
@EnableCaching
@Configuration
@Profile("audit-file-cache-test")
public class SpringConfig {

  @Bean
  LogLineContentProvider LogLineContentProvider() {
    return new LogLineContentProviderImpl();
  }

  @Bean
  CacheManager cacheManager() {
    SimpleCacheManager cacheMgr = new SimpleCacheManager();
    Cache cache = new ConcurrentMapCache(LogLineContentProviderImpl.AUDIT_FILES_CACHE);
    cacheMgr.setCaches(TransformerUtils.toList(cache));
    return cacheMgr;
  }
}
