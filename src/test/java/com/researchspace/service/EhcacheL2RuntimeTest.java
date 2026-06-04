package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runtime test that boots the production {@code ehcache.xml} through the real EhCache 3 / JSR-107
 * (JCache) provider - the same stack Hibernate 6 uses for the second-level cache via {@code
 * JCacheRegionFactory}. The standard test {@code SessionFactory} disables L2 ({@code
 * NoCachingRegionFactory}), so without this test no production cache region is ever instantiated by
 * CI.
 *
 * <p>No database is required: this exercises the cache layer directly. It complements the static
 * {@link EhcacheRegionConfigTest} (which checks region <em>names</em>) by checking the regions
 * actually <em>instantiate and operate</em> under EhCache 3.
 *
 * <p>It specifically guards RSDEV-444 review B4: the {@code ImageBlob} and {@code FileProperty}
 * regions previously declared {@code <key-type>java.lang.Long</key-type>}, but Hibernate 6 entity
 * L2 keys are {@code BasicCacheKeyImplementation} (not {@code Long}). A typed region (a) cannot be
 * fetched via single-arg {@code getCache} and (b) throws {@code ClassCastException} when Hibernate
 * stores its composite key - which would surface only in production.
 */
public class EhcacheL2RuntimeTest {

  private static final String EHCACHE_RESOURCE = "ehcache.xml";
  private static final String EHCACHE3_PROVIDER = "org.ehcache.jsr107.EhcacheCachingProvider";
  private static final String ENTITY_REGION_PREFIX = "com.researchspace.model";
  private static final Pattern ALIAS_PATTERN = Pattern.compile("alias=\"([^\"]+)\"");

  /** A non-Long, serializable stand-in for Hibernate's BasicCacheKeyImplementation entity key. */
  private static final Serializable HIBERNATE_STYLE_KEY =
      new Serializable() {
        @Override
        public String toString() {
          return "compositeEntityKey#42";
        }
      };

  private CachingProvider provider;
  private CacheManager cacheManager;

  @BeforeEach
  public void setUp() throws URISyntaxException {
    provider = Caching.getCachingProvider(EHCACHE3_PROVIDER);
    cacheManager =
        provider.getCacheManager(
            getClass().getClassLoader().getResource(EHCACHE_RESOURCE).toURI(),
            getClass().getClassLoader());
  }

  @AfterEach
  public void tearDown() {
    // Safe to close here: tests run with L2 disabled, so no Spring-managed SessionFactory shares
    // this CacheManager (the singleton-close hazard documented in applicationContext-dao.xml).
    if (cacheManager != null) {
      cacheManager.close();
    }
    if (provider != null) {
      provider.close();
    }
  }

  @Test
  public void everyEntityRegionInstantiatesUntyped() throws IOException {
    for (String region : readEntityRegionAliases()) {
      // Single-arg getCache only succeeds for an untyped (Object/Object) cache. A region declaring
      // key-type/value-type would throw IllegalArgumentException here - the B4 failure mode.
      assertNotNull(
          cacheManager.getCache(region),
          () ->
              region
                  + " did not instantiate as an untyped cache. If it declares <key-type>/"
                  + "<value-type> in ehcache.xml, remove them: Hibernate 6 L2 keys are not Long "
                  + "(see review B4).");
    }
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void cachedRegionsAcceptHibernateCompositeKeys() throws IOException {
    // The two regions that previously pinned key-type=Long (B4). Putting a non-Long key must not
    // throw - proving Hibernate's BasicCacheKeyImplementation would be accepted at runtime.
    for (String region :
        new String[] {
          "com.researchspace.model.ImageBlob", "com.researchspace.model.FileProperty"
        }) {
      javax.cache.Cache cache = cacheManager.getCache(region);
      assertNotNull(cache, region + " missing from ehcache.xml");
      assertDoesNotThrow(
          () -> cache.put(HIBERNATE_STYLE_KEY, "anyEntityValue"),
          region
              + " rejected a non-Long composite key - it still restricts key/value types (review"
              + " B4).");
    }
  }

  private Set<String> readEntityRegionAliases() throws IOException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(EHCACHE_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(EHCACHE_RESOURCE + " not found on the test classpath");
      }
      String xml = IOUtils.toString(in, StandardCharsets.UTF_8);
      Set<String> regions = new TreeSet<>();
      Matcher m = ALIAS_PATTERN.matcher(xml);
      while (m.find()) {
        String alias = m.group(1);
        if (alias.startsWith(ENTITY_REGION_PREFIX)) {
          regions.add(alias);
        }
      }
      return regions;
    }
  }
}
