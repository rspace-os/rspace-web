package com.researchspace.dao;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.ArchiveVersionToAppVersion;
import com.researchspace.model.Version;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class MetadataTest extends SpringTransactionalTest {
  @Autowired RSMetaDataDao dao;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGetSchemaVersions() {
    List<ArchiveVersionToAppVersion> versions = dao.getArchiveVersionsToAppVersion();
    assertTrue(versions.size() > 1);
  }

  @Test
  public void testGetSchemaVersion() {
    ArchiveVersionToAppVersion version = dao.getAppVersionForArchiveVersion(new Version(1L), "doc");
    assertNotNull(version);
  }
}
