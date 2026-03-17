package com.axiope.service.cfg;

import static org.junit.Assert.assertNotNull;

import javax.cache.Cache;
import javax.cache.CacheManager;
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

  // any number > 0
  static final int OVERRIDE_FILEPROPERTY_SIZE = 1012;
  static final int OVERRIDE_IMAGE_BLOB_SIZE_SIZE = 2312;

  static Cache<?, ?> getCache(String name, CacheManager cacheMgr) {
    return cacheMgr.getCache(name);
  }

  static void assertCachesPresent(CacheManager cacheMgr) {
    assertNotNull(getCache("com.researchspace.model.FileProperty", cacheMgr));
    assertNotNull(getCache("com.researchspace.model.ImageBlob", cacheMgr));
  }

  private @Autowired CacheManager jCacheManager;
  private @Autowired EhCacheConfigurer cacheConfigurer;

  @Test
  public void testEhCacheConfiguration() {
    // if not overridden, use defaults
    // factory.getObject will areturn the same object each time; it is a singleton
    cacheConfigurer.configure(jCacheManager);
    assertCachesPresent(jCacheManager);

    // set bad values, should be unchanged, defaults should work
    cacheConfigurer.setFilePropertyMaxElementsInMemory("abcde");
    cacheConfigurer.setImageBlobMaxElementsInMemory("-1234");
    cacheConfigurer.configure(jCacheManager);
    assertCachesPresent(jCacheManager);

    // override via property injection
    cacheConfigurer.setFilePropertyMaxElementsInMemory(OVERRIDE_FILEPROPERTY_SIZE + "");
    cacheConfigurer.setImageBlobMaxElementsInMemory(OVERRIDE_IMAGE_BLOB_SIZE_SIZE + "");
    cacheConfigurer.configure(jCacheManager);
    assertCachesPresent(jCacheManager);
  }
}
