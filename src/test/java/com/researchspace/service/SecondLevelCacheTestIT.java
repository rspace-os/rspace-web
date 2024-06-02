package com.researchspace.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.dao.ArchiveDao;
import com.researchspace.dao.EcatImageDao;
import com.researchspace.dao.IconImgDao;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import org.apache.commons.lang.time.StopWatch;
import org.hibernate.CacheMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;

@RunWith(ConditionalTestRunner.class)
public class SecondLevelCacheTestIT extends RealTransactionSpringTestBase {

  private static final int NUM_OBJECTS = 1000;

  private @Autowired IconImgDao iconDao;
  private @Autowired ArchiveDao archiveDao;
  private @Autowired EcatImageDao imageDao;
  private @Autowired DocumentHTMLPreviewHandler previewer;
  private @Autowired RecordManager recordMgr;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    sessionFactory.getStatistics().setStatisticsEnabled(true);
  }

  @After
  public void tearDown() throws Exception {
    sessionFactory.getStatistics().setStatisticsEnabled(false);
    contentInitializer.setCustomInitActive(true);
    super.tearDown();
  }

  @Test
  public void dummy() {
    // always runs so setup/cleanup is ok even if perf tests disabled.
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void test2ndLevelCacheofSimpleObject() throws Exception {
    CacheManager cacheMgr = getTestCache();

    // create 1000 objects
    List<String> ids = addNArchiveCSum(NUM_OBJECTS);

    System.err.println("loading cache...");
    Ehcache cache = cacheMgr.getEhcache("com.researchspace.model.ArchivalCheckSum");
    cache.setStatisticsEnabled(true);
    cache.setSampledStatisticsEnabled(true);
    openTransaction();

    sessionFactory.getCurrentSession().setCacheMode(CacheMode.NORMAL);
    // this will load from DB, and should populate cachde
    for (int i = 0; i < NUM_OBJECTS; i++) {
      archiveDao.get(ids.get(i));
    }
    commitTransaction();

    System.err.println("hitting cache..");
    openTransaction();
    // now, load again, should restore from cached
    long start = System.currentTimeMillis();
    for (int i = 0; i < NUM_OBJECTS; i++) {
      archiveDao.get(ids.get(i));
    }
    commitTransaction();
    long end = System.currentTimeMillis();
    long time = end - start;
    System.err.println(" cache :" + time);
    System.err.println(
        " hit count is " + sessionFactory.getStatistics().getSecondLevelCacheHitCount());

    openTransaction();
    sessionFactory.getCurrentSession().setCacheMode(CacheMode.IGNORE);
    sessionFactory.getStatistics().clear();
    long start2 = System.currentTimeMillis();
    for (int i = 0; i < 1000; i++) {
      archiveDao.get(ids.get(i));
    }
    commitTransaction();
    long end2 = System.currentTimeMillis();
    long time2 = end2 - start2;
    System.err.println(" no cache :" + time2);
    System.err.println(
        " hit count is " + sessionFactory.getStatistics().getSecondLevelCacheHitCount());
  }

  List<String> addNArchiveCSum(int n) {
    List<String> uuids = new ArrayList<String>();
    openTransaction();
    for (int i = 0; i < n; i++) {
      if (i % 500 == 0) {
        System.err.print(".");
        sessionFactory.getCurrentSession().flush();
      }
      ArchivalCheckSum acs = new ArchivalCheckSum();
      acs.setUid(CoreTestUtils.getRandomName(10));
      uuids.add(acs.getUid());
      acs.setCheckSum(123445);

      archiveDao.save(acs);
    }
    commitTransaction();
    return uuids;
  }

  protected CacheManager getTestCache() {
    CacheManager c =
        CacheManager.create(
            this.getClass().getClassLoader().getResourceAsStream("ehcache_DEV.xml"));
    return c;
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
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

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void test2ndLevelCacheofIconEntity() throws Exception {
    int numToCache = 100;
    List<Long> ids = saveNIconEntities(numToCache);
    CacheManager cacheMgr = getTestCache();
    Ehcache cache = cacheMgr.getEhcache("com.researchspace.model.record.IconEntity");
    cache.setStatisticsEnabled(true);
    cache.setSampledStatisticsEnabled(true);

    openTransaction();
    sessionFactory.getCurrentSession().setCacheMode(CacheMode.IGNORE);
    StopWatch stopWatch = new StopWatch();

    // add to cache
    for (Long id : ids) {
      iconDao.getIconEntity(id);
    }
    commitTransaction();
    openTransaction();
    stopWatch.start();
    // add to cache
    for (Long id : ids) {
      iconDao.getIconEntity(id);
    }
    System.err.println(
        "With caching of " + numToCache + " iconEntities : time =" + stopWatch.getTime() + " ms.");
    commitTransaction();
    openTransaction();
    sessionFactory.getCurrentSession().setCacheMode(CacheMode.IGNORE);
    sessionFactory.getStatistics().clear();
    stopWatch.reset();
    stopWatch.start();
    // we're calling each object once, so will not get 1st level cache hits, only 2ndlevel.
    for (Long id : ids) {
      iconDao.getIconEntity(id);
    }
    System.err.println(
        "With NO caching of "
            + numToCache
            + " iconEntities : time ="
            + stopWatch.getTime()
            + " ms.");
    commitTransaction();
  }

  private List<Long> saveNIconEntities(int numObjects) {
    openTransaction();
    Random randGen = new Random();
    List<Long> ids = new ArrayList<Long>();
    for (int i = 0; i < numObjects; i++) {
      IconEntity ie = new IconEntity();
      byte[] random = new byte[1000];
      randGen.nextBytes(random);
      ie.setIconImage(random);
      ie.setHeight(30);
      ie.setWidth(20);
      ie.setImgType("png");
      ie.setImgName("random" + i);
      ie.setParentId(1L); // any value ok for testing
      iconDao.saveIconEntity(ie, true);
      ids.add(ie.getId());
      if (i % 100 == 0) {
        sessionFactory.getCurrentSession().flush();
      }
    }
    commitTransaction();
    return ids;
  }
}
