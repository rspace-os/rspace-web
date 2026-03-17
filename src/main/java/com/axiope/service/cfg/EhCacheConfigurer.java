package com.axiope.service.cfg;

import static com.researchspace.CacheNames.RAID_CONNECTION;

import com.researchspace.CacheNames;
import com.researchspace.core.util.NumberUtils;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * This is in its own class so as to separate the configuration from the creation of the EhCache
 * beans.
 */
class EhCacheConfigurer {
  private static final Logger log = LoggerFactory.getLogger(EhCacheConfigurer.class);

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

  @Value("${cache." + RAID_CONNECTION + "}")
  private String raidAliveConnectionMaxElementsInMemory;

  @Value("${cache.com.researchspace.model.record.BaseRecord}")
  private String baseRecordMaxElementsInMemory;

  @Value("${cache.com.researchspace.model.UserGroup}")
  private String userGroupMaxElementsInMemory;

  @Value("${cache.com.researchspace.model.UserPreference}")
  private String userPreferenceMaxElementsInMemory;

  public EhCacheConfigurer() {}

  public void configure(CacheManager cacheManager) {
    warnOverride(imageBlobMaxElementsInMemory, imageBlobCachename);
    warnOverride(filePropertyMaxElementsInMemory, filePropertyCachename);
    warnOverride(userRoleMaxElementsInMemory, userRolesCachename);
    warnOverride(fieldFormMaxElementsInMemory, fieldFormCachename);
    warnOverride(formMaxElementsInMemory, formCachename);
    warnOverride(integrationInfoMaxElementsInMemory, CacheNames.INTEGRATION_INFO);
    warnOverride(raidAliveConnectionMaxElementsInMemory, RAID_CONNECTION);
    warnOverride(baseRecordMaxElementsInMemory, "com.researchspace.model.record.BaseRecord");
    warnOverride(userGroupMaxElementsInMemory, "com.researchspace.model.UserGroup");
    warnOverride(userPreferenceMaxElementsInMemory, "com.researchspace.model.UserPreference");

    ensureCachePresent(cacheManager, filePropertyCachename);
    ensureCachePresent(cacheManager, imageBlobCachename);
    ensureCachePresent(cacheManager, userRolesCachename);
    ensureCachePresent(cacheManager, fieldFormCachename);
    ensureCachePresent(cacheManager, formCachename);
    ensureCachePresent(cacheManager, CacheNames.INTEGRATION_INFO);
    ensureCachePresent(cacheManager, RAID_CONNECTION);
    ensureCachePresent(cacheManager, "com.researchspace.model.record.BaseRecord");
    ensureCachePresent(cacheManager, "com.researchspace.model.UserGroup");
    ensureCachePresent(cacheManager, "com.researchspace.model.UserPreference");
  }

  private void warnOverride(String value, String cachename) {
    if (isCacheSizeOverrideDefined(value) || isCacheSizeSetToZero(value)) {
      log.warn(
          "Cache override for [{}] is set to [{}] but JCache/Ehcache3 configuration "
              + "is not mutable at runtime. Configure this in ehcache.xml instead.",
          cachename,
          value);
    }
  }

  private boolean isCacheSizeOverrideDefined(String cacheOverridValue) {
    return NumberUtils.stringToInt(cacheOverridValue, -1) > 0;
  }

  private boolean isCacheSizeSetToZero(String cacheOverridValue) {
    return NumberUtils.stringToInt(cacheOverridValue, -1) == 0;
  }

  private void ensureCachePresent(CacheManager cacheManager, String cacheName) {
    Cache<?, ?> cache = cacheManager.getCache(cacheName);
    if (cache == null) {
      cacheManager.createCache(cacheName, new MutableConfiguration<>());
    }
  }

  // for testing
  void setFilePropertyMaxElementsInMemory(String filePropertyMaxElementsInMemory) {
    this.filePropertyMaxElementsInMemory = filePropertyMaxElementsInMemory;
  }

  void setImageBlobMaxElementsInMemory(String imageBlobMaxElementsInMemory) {
    this.imageBlobMaxElementsInMemory = imageBlobMaxElementsInMemory;
  }
}
