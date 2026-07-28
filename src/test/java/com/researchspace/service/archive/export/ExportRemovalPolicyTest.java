package com.researchspace.service.archive.export;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;

public class ExportRemovalPolicyTest {

  @Test
  public void testConstantPolicies() {
    assertTrue(ExportRemovalPolicy.TRUE.removeExport(TestFactory.createAnArchivalChecksum()));
    assertFalse(ExportRemovalPolicy.FALSE.removeExport(TestFactory.createAnArchivalChecksum()));
  }
}
