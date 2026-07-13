package com.axiope.service.cfg;

import java.io.IOException;
import java.net.URI;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Configure caching settings. <br>
 * Caching is defined in ehcache.xml </br/>
 *
 * <p>To override:
 *
 * <ul>
 *   <li>Set a deployment property named after the cache, prefixed with 'cache'
 *   <li>Add a new injected property value
 *   <li>Set into the cache configuration
 * </ul>
 *
 * Runtime cache sizes can be viewed in JavaMelody Monitoring page
 */
@Configuration
@EnableCaching
public class CacheConfig {
  private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

  @Value("${cache.apply:true}")
  private String useCacheStr = "true";

  /**
   * Gets Spring CacheManage ( ehcache) or a no-op manager depending on value of deployment property
   * 'cache.apply'
   *
   * @return
   */
  @Bean
  public CacheManager cacheManager(@Autowired EhCacheConfigurer configurer) {
    Boolean useCache = Boolean.parseBoolean(useCacheStr);
    if (useCache) {
      javax.cache.CacheManager jCacheManager = jCacheManager();
      configurer.configure(jCacheManager);
      return new JCacheCacheManager(jCacheManager);
    } else {
      return new NoOpCacheManager();
    }
  }

  @Bean
  EhCacheConfigurer ehCacheConfigurer() {
    return new EhCacheConfigurer();
  }

  @Bean(destroyMethod = "")
  javax.cache.CacheManager jCacheManager() {
    CachingProvider provider = Caching.getCachingProvider();
    URI configUri = getEhcacheConfigUri();
    if (configUri != null) {
      try {
        return provider.getCacheManager(configUri, getClass().getClassLoader());
      } catch (RuntimeException e) {
        log.warn("Unable to load ehcache.xml; falling back to default JCache manager", e);
      }
    }
    return provider.getCacheManager();
  }

  private URI getEhcacheConfigUri() {
    try {
      return new ClassPathResource("ehcache.xml").getURI();
    } catch (IOException e) {
      log.warn("Unable to locate ehcache.xml; using default JCache manager", e);
      return null;
    }
  }
}
