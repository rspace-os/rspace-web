package com.axiope.service.cfg;

import static com.researchspace.CacheNames.RAID_CONNECTION;

import com.researchspace.CacheNames;
import com.researchspace.core.util.NumberUtils;
import javax.cache.Cache;
import javax.cache.CacheManager;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
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
    applyOverride(cacheManager, imageBlobMaxElementsInMemory, imageBlobCachename);
    applyOverride(cacheManager, filePropertyMaxElementsInMemory, filePropertyCachename);
    applyOverride(cacheManager, userRoleMaxElementsInMemory, userRolesCachename);
    applyOverride(cacheManager, fieldFormMaxElementsInMemory, fieldFormCachename);
    applyOverride(cacheManager, formMaxElementsInMemory, formCachename);
    applyOverride(cacheManager, integrationInfoMaxElementsInMemory, CacheNames.INTEGRATION_INFO);
    applyOverride(cacheManager, raidAliveConnectionMaxElementsInMemory, RAID_CONNECTION);
    applyOverride(
        cacheManager, baseRecordMaxElementsInMemory, "com.researchspace.model.record.BaseRecord");
    applyOverride(cacheManager, userGroupMaxElementsInMemory, "com.researchspace.model.UserGroup");
    applyOverride(
        cacheManager, userPreferenceMaxElementsInMemory, "com.researchspace.model.UserPreference");
  }

  private void applyOverride(CacheManager cacheManager, String value, String cacheName) {
    if (isCacheSizeSetToZero(value)) {
      log.warn(
          "Cache override for [{}] is set to 0 (disable) which is not supported"
              + " at runtime with JCache/Ehcache3. Configure this in ehcache.xml instead.",
          cacheName);
      return;
    }
    if (!isCacheSizeOverrideDefined(value)) {
      return;
    }
    int size = NumberUtils.stringToInt(value, -1);
    Cache<?, ?> jcache = cacheManager.getCache(cacheName);
    if (jcache == null) {
      log.warn("Cache [{}] not found in CacheManager, cannot apply override.", cacheName);
      return;
    }
    try {
      org.ehcache.Cache<?, ?> nativeCache =
          (org.ehcache.Cache<?, ?>) jcache.unwrap(org.ehcache.Cache.class);
      nativeCache
          .getRuntimeConfiguration()
          .updateResourcePools(
              ResourcePoolsBuilder.newResourcePoolsBuilder().heap(size, EntryUnit.ENTRIES).build());
      log.info("Applied cache heap override for [{}]: {} entries", cacheName, size);
    } catch (Exception e) {
      log.warn("Failed to apply cache override for [{}]: {}", cacheName, e.getMessage());
    }
  }

  private boolean isCacheSizeOverrideDefined(String cacheOverridValue) {
    return NumberUtils.stringToInt(cacheOverridValue, -1) > 0;
  }

  private boolean isCacheSizeSetToZero(String cacheOverridValue) {
    return NumberUtils.stringToInt(cacheOverridValue, -1) == 0;
  }

  // for testing
  void setFilePropertyMaxElementsInMemory(String filePropertyMaxElementsInMemory) {
    this.filePropertyMaxElementsInMemory = filePropertyMaxElementsInMemory;
  }

  void setImageBlobMaxElementsInMemory(String imageBlobMaxElementsInMemory) {
    this.imageBlobMaxElementsInMemory = imageBlobMaxElementsInMemory;
  }
}
