package com.axiope.service.cfg;

import static org.junit.Assert.assertEquals;

import net.sf.ehcache.Cache;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration(classes = {CacheConfig.class})
public class EhCacheConfigurerConfigTest extends AbstractJUnit4SpringContextTests {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  // from ehcache.xml
  static final int DEFAULT_FILEPROPERTY_SIZE = 2500;
  static final int DEFAULT_IMAGE_BLOB_SIZE_SIZE = 1000;

  // any number > 0
  static final int OVERRIDE_FILEPROPERTY_SIZE = 1012;
  static final int OVERRIDE_IMAGE_BLOB_SIZE_SIZE = 2312;

  static Cache getNativeCache(String name, CacheManager cacheMgr) {
    return (Cache) cacheMgr.getCache(name).getNativeCache();
  }

  static void assertCacheSettingsUseDefaults(CacheManager cacheMgr) {
    net.sf.ehcache.Cache cache = getNativeCache("com.researchspace.model.FileProperty", cacheMgr);
    assertEquals(DEFAULT_FILEPROPERTY_SIZE, cache.getCacheConfiguration().getMaxEntriesLocalHeap());
    cache = getNativeCache("com.researchspace.model.ImageBlob", cacheMgr);
    assertEquals(
        DEFAULT_IMAGE_BLOB_SIZE_SIZE, cache.getCacheConfiguration().getMaxEntriesLocalHeap());
  }

  private @Autowired CacheManager cacheMgr;
  private @Autowired EhCacheConfigurer cacheConfigurer;
  private @Autowired EhCacheManagerFactoryBean factory;

  @Test
  public void testEhCacheConfiguration() {
    // if not overridden, use defaults
    // factory.getObject will areturn the same object each time; it is a singleton
    cacheConfigurer.configure(factory.getObject());
    assertCacheSettingsUseDefaults(cacheMgr);

    // set bad values, should be unchanged, defaults should work
    cacheConfigurer.setFilePropertyMaxElementsInMemory("abcde");
    cacheConfigurer.setImageBlobMaxElementsInMemory("-1234");
    cacheConfigurer.configure(factory.getObject());
    assertCacheSettingsUseDefaults(cacheMgr);

    // override via property injection
    cacheConfigurer.setFilePropertyMaxElementsInMemory(OVERRIDE_FILEPROPERTY_SIZE + "");
    cacheConfigurer.setImageBlobMaxElementsInMemory(OVERRIDE_IMAGE_BLOB_SIZE_SIZE + "");
    cacheConfigurer.configure(factory.getObject());
    net.sf.ehcache.Cache cache = getNativeCache("com.researchspace.model.FileProperty", cacheMgr);

    assertEquals(
        OVERRIDE_FILEPROPERTY_SIZE, cache.getCacheConfiguration().getMaxEntriesLocalHeap());
    cache = getNativeCache("com.researchspace.model.ImageBlob", cacheMgr);
    assertEquals(
        OVERRIDE_IMAGE_BLOB_SIZE_SIZE, cache.getCacheConfiguration().getMaxEntriesLocalHeap());
  }
}
