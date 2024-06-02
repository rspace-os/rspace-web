package com.researchspace.service;

import static org.junit.Assert.assertTrue;

import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DatabaseMetaDataManagerTest extends SpringTransactionalTest {
  private static final int MARIA_DB10 = 10;
  private static final int MYSQL_5 = 5;
  @Autowired private DatabaseMetaDataManager mgr;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testGetVersion() {
    int version = mgr.getVersion().getMajor();
    assertTrue(version == MARIA_DB10 || version == MYSQL_5);
  }

  @Test
  public void testGetVersionMessage() {
    System.err.println(mgr.getVersionMessage());
  }
}
