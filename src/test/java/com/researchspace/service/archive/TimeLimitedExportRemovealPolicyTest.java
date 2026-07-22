package com.researchspace.service.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.testutils.TestFactory;
import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TimeLimitedExportRemovealPolicyTest {

  private TimeLimitedExportRemovalPolicy policy;

  @Before
  public void setUp() throws Exception {
    policy = new TimeLimitedExportRemovalPolicy(new MessageSourceUtils(new JsonMessageSource()));
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGetStorageTime() {
    assertNotEquals(0, policy.getStorageTime());
    assertEquals(TimeLimitedExportRemovalPolicy.DEFAULT_LENGTH, policy.getStorageTime());
  }

  @Test
  public void testGetStorageTimeInvalid() {
    policy.setStorageTimeProperty("23.2"); // invalid
    assertEquals(TimeLimitedExportRemovalPolicy.DEFAULT_LENGTH, policy.getStorageTime());
  }

  @Test
  public void testGetStorageValidProperty() {
    policy.setStorageTimeProperty("23"); // valid
    assertEquals(23, policy.getStorageTime());
  }

  @Test
  public void testSetStorageTimeProperty() {
    policy.setStorageTime(-1); // invalid
    assertEquals(TimeLimitedExportRemovalPolicy.DEFAULT_LENGTH, policy.getStorageTime());

    policy.setStorageTime(1); // valid
    assertEquals(1, policy.getStorageTime());
  }

  @Test
  public void testremoveExport() {
    // dated  from now
    ArchivalCheckSum acs = TestFactory.createAnArchivalChecksum();
    policy.setStorageTime(1);
    assertFalse(policy.removeExport(acs));
    Date moreThanAnHourAgo = new Date(acs.getArchivalDate() - 2 * 3600 * 1000);
    acs.setArchivalDate(moreThanAnHourAgo.getTime());
    assertTrue(policy.removeExport(acs));
  }

  @Test
  public void formatsRemovalMessageForSingularAndPluralHours() {
    policy.setStorageTime(1);
    assertEquals(
        "The export will be eligible for deletion after 1 hour.",
        policy.getRemovalCircumstancesMsg());

    policy.setStorageTime(2);
    assertEquals(
        "The export will be eligible for deletion after 2 hours.",
        policy.getRemovalCircumstancesMsg());
  }
}
