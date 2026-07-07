package com.researchspace.service;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DatabaseMetaDataManagerTest extends SpringTransactionalTest {
  private static final int MIN_MAJOR = 10;
  private static final int MIN_MINOR = 11;
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
    SemanticVersion version = mgr.getVersion();
    boolean atLeastMinimum =
        version.getMajor() > MIN_MAJOR
            || (version.getMajor() == MIN_MAJOR && version.getMinor() >= MIN_MINOR);
    assertTrue("Expected MariaDB >= 10.11 but got " + version, atLeastMinimum);
  }
}
