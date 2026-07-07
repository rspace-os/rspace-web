package com.researchspace.service;

import static org.junit.Assert.assertTrue;

import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DatabaseMetaDataManagerTest extends SpringTransactionalTest {
  private static final int MIN_MARIADB_MAJOR = 10;
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
    assertTrue("Expected MariaDB 10+ but got major " + version, version >= MIN_MARIADB_MAJOR);
  }
}
