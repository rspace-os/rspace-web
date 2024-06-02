package com.axiope.service.cfg;

import com.researchspace.CacheNames;
import com.researchspace.core.util.NumberUtils;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import org.springframework.beans.factory.annotation.Value;

/**
 * This is in its own class so as to separate the configuration from the creation of the EhCache
 * beans.
 */
class EhCacheConfigurer {

  static final String filePropertyCachename = "com.researchspace.model.FileProperty";
  static final String imageBlobCachename = "com.researchspace.model.ImageBlob";
  static final String userRolesCachename = "com.researchspace.model.User.roles";
  static final String fieldFormCachename = "com.researchspace.model.field.FieldForm";
  static final String formCachename = "com.researchspace.model.record.RSForm";

  @Value("${cache." + filePropertyCachename + "}")
  private String filePropertyMaxElementsInMemory;

  @Value("${cache." + imageBlobCachename + "}")
  private String imageBlobMaxElementsInMemory;

  @Value("${cache." + userRolesCachename + "}")
  private String userRoleMaxElementsInMemory;

  @Value("${cache." + fieldFormCachename + "}")
  private String fieldFormMaxElementsInMemory;

  @Value("${cache." + formCachename + "}")
  private String formMaxElementsInMemory;

  @Value("${cache.com.researchspace.model.dto.IntegrationInfo}")
  private String integrationInfoMaxElementsInMemory;

  @Value("${cache.com.researchspace.model.record.BaseRecord}")
  private String baseRecordMaxElementsInMemory;

  @Value("${cache.com.researchspace.model.UserGroup}")
  private String userGroupMaxElementsInMemory;

  @Value("${cache.com.researchspace.model.UserPreference}")
  private String userPreferenceMaxElementsInMemory;

  public EhCacheConfigurer() {}

  public void configure(CacheManager ehCcheMgr) {
    override(ehCcheMgr, imageBlobMaxElementsInMemory, imageBlobCachename);
    override(ehCcheMgr, filePropertyMaxElementsInMemory, filePropertyCachename);
    override(ehCcheMgr, userRoleMaxElementsInMemory, userRolesCachename);
    override(ehCcheMgr, fieldFormMaxElementsInMemory, fieldFormCachename);
    override(ehCcheMgr, formMaxElementsInMemory, formCachename);
    override(ehCcheMgr, integrationInfoMaxElementsInMemory, CacheNames.INTEGRATION_INFO);
    override(ehCcheMgr, baseRecordMaxElementsInMemory, "com.researchspace.model.record.BaseRecord");
    override(ehCcheMgr, userGroupMaxElementsInMemory, "com.researchspace.model.UserGroup");
    override(
        ehCcheMgr, userPreferenceMaxElementsInMemory, "com.researchspace.model.UserPreference");
  }

  private void override(CacheManager ehCcheMgr, String value, String cachename) {
    if (isCacheSizeOverrideDefined(value)) {
      CacheConfiguration config = getCacheConfigurationForCache(ehCcheMgr, cachename);
      config.setMaxEntriesLocalHeap(Integer.parseInt(value));
    }
    if (isCacheSizeSetToZero(value)) {
      disableCache(ehCcheMgr, cachename);
    }
  }

  private boolean isCacheSizeOverrideDefined(String cacheOverridValue) {
    return NumberUtils.stringToInt(cacheOverridValue, -1) > 0;
  }

  private boolean isCacheSizeSetToZero(String cacheOverridValue) {
    return NumberUtils.stringToInt(cacheOverridValue, -1) == 0;
  }

  private CacheConfiguration getCacheConfigurationForCache(CacheManager mgr, String cacheName) {
    Cache cache = mgr.getCache(cacheName);
    return cache.getCacheConfiguration();
  }

  private void disableCache(CacheManager mgr, String cacheName) {
    Cache cache = mgr.getCache(cacheName);
    cache.setDisabled(true);
  }

  // for testing
  void setFilePropertyMaxElementsInMemory(String filePropertyMaxElementsInMemory) {
    this.filePropertyMaxElementsInMemory = filePropertyMaxElementsInMemory;
  }

  void setImageBlobMaxElementsInMemory(String imageBlobMaxElementsInMemory) {
    this.imageBlobMaxElementsInMemory = imageBlobMaxElementsInMemory;
  }
}
