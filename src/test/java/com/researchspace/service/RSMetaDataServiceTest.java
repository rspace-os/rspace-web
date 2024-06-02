package com.researchspace.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.Version;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RSMetaDataServiceTest extends SpringTransactionalTest {

  @Autowired RSMetaDataManager metadataMgr;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testIsArchiveImportable() {
    assertTrue(metadataMgr.isArchiveImportable("doc", new Version(1L)));
    // too late
    assertFalse(metadataMgr.isArchiveImportable("doc", new Version(20L)));
    // unknown schema type
    assertFalse(metadataMgr.isArchiveImportable("docxxxx", new Version(1L)));
  }
}
