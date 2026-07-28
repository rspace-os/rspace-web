package com.axiope.service.cfg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.cache.CacheManager;
import org.ehcache.Cache;
import org.ehcache.config.ResourceType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration(classes = {CacheConfig.class})
public class EhCacheConfigurerConfigTest extends AbstractJUnit4SpringContextTests {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  // from ehcache.xml
  static final long DEFAULT_FILEPROPERTY_SIZE = 2500;
  static final long DEFAULT_IMAGE_BLOB_SIZE = 1000;

  // any number > 0
  static final int OVERRIDE_FILEPROPERTY_SIZE = 1012;
  static final int OVERRIDE_IMAGE_BLOB_SIZE = 2312;

  static Cache<?, ?> getNativeCache(String name, CacheManager cacheMgr) {
    javax.cache.Cache<?, ?> jcache = cacheMgr.getCache(name);
    assertNotNull("Cache " + name + " not found", jcache);
    return (Cache<?, ?>) jcache.unwrap(Cache.class);
  }

  static long getHeapSize(Cache<?, ?> cache) {
    return cache
        .getRuntimeConfiguration()
        .getResourcePools()
        .getPoolForResource(ResourceType.Core.HEAP)
        .getSize();
  }

  static void assertCacheSettingsUseDefaults(CacheManager cacheMgr) {
    assertEquals(
        DEFAULT_FILEPROPERTY_SIZE,
        getHeapSize(getNativeCache("com.researchspace.model.FileProperty", cacheMgr)));
    assertEquals(
        DEFAULT_IMAGE_BLOB_SIZE,
        getHeapSize(getNativeCache("com.researchspace.model.ImageBlob", cacheMgr)));
  }

  private @Autowired CacheManager jCacheManager;
  private @Autowired EhCacheConfigurer cacheConfigurer;

  @Test
  public void testEhCacheConfiguration() {
    // if not overridden, use defaults
    cacheConfigurer.configure(jCacheManager);
    assertCacheSettingsUseDefaults(jCacheManager);

    // set bad values, should be unchanged, defaults should work
    cacheConfigurer.setFilePropertyMaxElementsInMemory("abcde");
    cacheConfigurer.setImageBlobMaxElementsInMemory("-1234");
    cacheConfigurer.configure(jCacheManager);
    assertCacheSettingsUseDefaults(jCacheManager);

    // override via property injection
    cacheConfigurer.setFilePropertyMaxElementsInMemory(OVERRIDE_FILEPROPERTY_SIZE + "");
    cacheConfigurer.setImageBlobMaxElementsInMemory(OVERRIDE_IMAGE_BLOB_SIZE + "");
    cacheConfigurer.configure(jCacheManager);

    assertEquals(
        OVERRIDE_FILEPROPERTY_SIZE,
        getHeapSize(getNativeCache("com.researchspace.model.FileProperty", jCacheManager)));
    assertEquals(
        OVERRIDE_IMAGE_BLOB_SIZE,
        getHeapSize(getNativeCache("com.researchspace.model.ImageBlob", jCacheManager)));
  }
}
