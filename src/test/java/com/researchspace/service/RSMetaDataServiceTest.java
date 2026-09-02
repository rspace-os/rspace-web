package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.Version;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RSMetaDataServiceTest extends SpringTransactionalTest {

  @Autowired RSMetaDataManager metadataMgr;

  @Test
  public void testIsArchiveImportable() {
    assertTrue(metadataMgr.isArchiveImportable("doc", new Version(1L)));
    // too late
    assertFalse(metadataMgr.isArchiveImportable("doc", new Version(20L)));
    // unknown schema type
    assertFalse(metadataMgr.isArchiveImportable("docxxxx", new Version(1L)));
  }
}
