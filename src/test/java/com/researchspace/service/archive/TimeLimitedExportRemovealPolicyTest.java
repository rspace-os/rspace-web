package com.researchspace.service.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.archive.export.ExportRemovalPolicy.RemovalCircumstancesMessage;
import com.researchspace.testutils.TestFactory;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TimeLimitedExportRemovealPolicyTest {

  private TimeLimitedExportRemovalPolicy policy;

  @BeforeEach
  public void setUp() throws Exception {
    policy = new TimeLimitedExportRemovalPolicy();
  }

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
    MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
    policy.setStorageTime(1);
    RemovalCircumstancesMessage singular = policy.getRemovalCircumstancesMessage();
    assertEquals(
        "The export will be eligible for deletion after 1 hour.",
        messages.format(singular.key(), singular.arguments()));

    policy.setStorageTime(2);
    RemovalCircumstancesMessage plural = policy.getRemovalCircumstancesMessage();
    assertEquals(
        "The export will be eligible for deletion after 2 hours.",
        messages.format(plural.key(), plural.arguments()));
  }
}
