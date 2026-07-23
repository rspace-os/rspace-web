package com.researchspace.service.archive.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.testutils.TestFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ExportRemovalPolicyTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testConstantPolicies() {
    assertTrue(ExportRemovalPolicy.TRUE.removeExport(TestFactory.createAnArchivalChecksum()));
    assertFalse(ExportRemovalPolicy.FALSE.removeExport(TestFactory.createAnArchivalChecksum()));
    assertEquals(
        "email.notification.exportCompleteNotification.removalPolicyNow",
        ExportRemovalPolicy.TRUE.getRemovalCircumstancesMessage().key());
    assertEquals(
        "email.notification.exportCompleteNotification.removalPolicyNever",
        ExportRemovalPolicy.FALSE.getRemovalCircumstancesMessage().key());
  }
}
