package com.axiope.service.cfg;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
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
@EnableCaching()
public class CacheConfig {

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
      EhCacheCacheManager rc = new EhCacheCacheManager();
      net.sf.ehcache.CacheManager mgr = createNativeCacheManager();
      configurer.configure(mgr);
      rc.setCacheManager(mgr);
      return rc;
    } else {
      return new NoOpCacheManager();
    }
  }

  net.sf.ehcache.CacheManager createNativeCacheManager() {
    return (net.sf.ehcache.CacheManager) ehCacheManagerFactoryBean().getObject();
  }

  @Bean
  EhCacheConfigurer ehCacheConfigurer() {
    return new EhCacheConfigurer();
  }

  @Bean(name = "ehCache")
  EhCacheManagerFactoryBean ehCacheManagerFactoryBean() {
    EhCacheManagerFactoryBean rc =
        new org.springframework.cache.ehcache.EhCacheManagerFactoryBean();
    rc.setShared(true);
    rc.setConfigLocation(new ClassPathResource("ehcache.xml"));
    return rc;
  }
}
