package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DatabaseMetaDataManagerTest extends SpringTransactionalTest {
  private static final int MIN_MAJOR = 10;
  private static final int MIN_MINOR = 11;
  @Autowired private DatabaseMetaDataManager mgr;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testGetVersion() {
    SemanticVersion version = mgr.getVersion();
    log.info("DB version: {}", version);
    boolean atLeastMinimum =
        version.getMajor() > MIN_MAJOR
            || (version.getMajor() == MIN_MAJOR && version.getMinor() >= MIN_MINOR);
    assertTrue(atLeastMinimum, "Expected MariaDB >= 10.11 but got " + version);
  }
}
