package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.net.URI;
import java.util.function.Function;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import org.ehcache.config.ResourceType;
import org.ehcache.config.SizedResourcePool;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.stat.Statistics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Exercises the production Hibernate second-level cache stack: {@code JCacheRegionFactory} backed
 * by EhCache 3 configured from {@code ehcache.xml}, using the same {@code hibernate.javax.cache.*}
 * settings as {@code applicationContext-dao.xml}.
 *
 * <p>The shared test {@code SessionFactory} disables L2 ({@code NoCachingRegionFactory}) to avoid
 * JCache CacheManager lifecycle conflicts between Spring test contexts, so these tests build a
 * dedicated {@code SessionFactory} with the production cache settings against the test database.
 *
 * <p>Besides hit/miss behaviour this guards the config wiring itself: with a wrong provider/uri
 * key, hibernate-jcache silently falls back to an empty default CacheManager and every region
 * becomes an untuned create-warn default, ignoring all of {@code ehcache.xml}.
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class SecondLevelCacheTestIT extends RealTransactionSpringTestBase {

  private static final String EHCACHE3_PROVIDER = "org.ehcache.jsr107.EhcacheCachingProvider";
  private static final String ICON_REGION = "com.researchspace.model.record.IconEntity";

  private @Autowired DocumentHTMLPreviewHandler previewer;
  private @Autowired RecordManager recordMgr;

  private SessionFactory l2SessionFactory;

  @Before
  public void setUpL2SessionFactory() throws Exception {
    super.setUp();
    StandardServiceRegistry registry =
        new StandardServiceRegistryBuilder()
            .applySetting(AvailableSettings.DATASOURCE, dataSource)
            .applySetting(AvailableSettings.USE_SECOND_LEVEL_CACHE, "true")
            .applySetting(
                AvailableSettings.CACHE_REGION_FACTORY,
                "org.hibernate.cache.jcache.JCacheRegionFactory")
            .applySetting("hibernate.javax.cache.provider", EHCACHE3_PROVIDER)
            .applySetting("hibernate.javax.cache.uri", "ehcache.xml")
            .applySetting(AvailableSettings.GENERATE_STATISTICS, "true")
            .applySetting("hibernate.search.enabled", "false")
            .build();
    l2SessionFactory =
        new MetadataSources(registry)
            .addAnnotatedClass(IconEntity.class)
            .buildMetadata()
            .buildSessionFactory();
  }

  @After
  public void tearDownL2SessionFactory() throws Exception {
    if (l2SessionFactory != null) {
      // closes the JCache CacheManager the region factory resolved (releaseFromUse)
      l2SessionFactory.close();
    }
    contentInitializer.setCustomInitActive(true);
    super.tearDown();
  }

  @Test
  public void secondLevelCacheServesEntityReadsAcrossSessions() {
    Long id = saveIconEntity();
    try {
      l2SessionFactory.getCache().evictAllRegions();
      Statistics stats = l2SessionFactory.getStatistics();
      stats.clear();

      inL2Transaction(session -> session.get(IconEntity.class, id));
      assertEquals(
          "first read in a fresh session must miss", 1, stats.getSecondLevelCacheMissCount());
      assertEquals("first read must populate the region", 1, stats.getSecondLevelCachePutCount());

      inL2Transaction(session -> session.get(IconEntity.class, id));
      assertEquals(
          "second read in a fresh session must be served from L2",
          1,
          stats.getSecondLevelCacheHitCount());
    } finally {
      deleteIconEntity(id);
    }
  }

  @Test
  public void hibernateRegionsAreBackedByEhcacheXmlConfiguration() throws Exception {
    Long id = saveIconEntity();
    try {
      l2SessionFactory.getCache().evictAllRegions();
      inL2Transaction(session -> session.get(IconEntity.class, id));

      // Resolve the CacheManager exactly as JCacheRegionFactory does (same uri + classloader
      // yields the same instance) and verify Hibernate's region is the ehcache.xml-configured
      // cache, not a create-warn default in some other manager.
      CachingProvider provider = Caching.getCachingProvider(EHCACHE3_PROVIDER);
      URI uri = getClass().getClassLoader().getResource("ehcache.xml").toURI();
      CacheManager cacheManager = provider.getCacheManager(uri, provider.getDefaultClassLoader());
      Cache<Object, Object> iconRegion = cacheManager.getCache(ICON_REGION);
      assertNotNull(ICON_REGION + " region missing from the ehcache.xml CacheManager", iconRegion);

      SizedResourcePool heap =
          iconRegion
              .unwrap(org.ehcache.Cache.class)
              .getRuntimeConfiguration()
              .getResourcePools()
              .getPoolForResource(ResourceType.Core.HEAP);
      assertEquals(
          "region must carry the ehcache.xml sizing, not a create-warn default",
          1000,
          heap.getSize());

      boolean hasEntry = iconRegion.iterator().hasNext();
      assertTrue("Hibernate's L2 put must land in the ehcache.xml-configured region", hasEntry);
    } finally {
      deleteIconEntity(id);
    }
  }

  @Test
  public void cacheDocHtmlView() throws Exception {
    User any = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(any, "some text");
    DocHtmlPreview preview = previewer.generateHtmlPreview(doc.getId(), any);
    // should be cached; same instance
    assertTrue(preview == previewer.generateHtmlPreview(doc.getId(), any));
    // should trigger cache eviction of preview
    recordMgr.saveStructuredDocument(doc.getId(), any.getUsername(), true, new ErrorList());
    assertFalse(preview == previewer.generateHtmlPreview(doc.getId(), any));
  }

  private Long saveIconEntity() {
    return inL2Transaction(
        session -> {
          IconEntity icon = new IconEntity();
          icon.setIconImage(new byte[] {1, 2, 3});
          icon.setImgName("l2-cache-test");
          icon.setImgType("png");
          icon.setParentId(1L);
          session.persist(icon);
          return icon.getId();
        });
  }

  private void deleteIconEntity(Long id) {
    inL2Transaction(
        session -> {
          IconEntity saved = session.get(IconEntity.class, id);
          if (saved != null) {
            session.remove(saved);
          }
          return null;
        });
  }

  private <T> T inL2Transaction(Function<Session, T> work) {
    try (Session session = l2SessionFactory.openSession()) {
      session.beginTransaction();
      try {
        T result = work.apply(session);
        session.getTransaction().commit();
        return result;
      } catch (RuntimeException e) {
        session.getTransaction().rollback();
        throw e;
      }
    }
  }
}
