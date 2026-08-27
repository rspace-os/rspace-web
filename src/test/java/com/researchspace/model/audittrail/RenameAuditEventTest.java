package com.researchspace.model.audittrail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.core.Person;
import org.junit.jupiter.api.Test;

public class RenameAuditEventTest {

  Person user = TestFactory.createAnyUser("any");

  @Test
  public void testGetAuditActionThrowsIAEIfMapNUll() {
    assertThrows(IllegalArgumentException.class, this::createInvalidRenameEvent);
  }

  @Test
  public void testGetDescription() {
    RenameAuditEvent event = createValidRenameEvent();
    assertEquals(AuditAction.RENAME, event.getAuditAction());
    assertEquals("from: \"oldName\" to: \"newName\"", event.getDescription());
  }

  private RenameAuditEvent createValidRenameEvent() {
    return new RenameAuditEvent(user, new AuditTrailTestObject(), "oldName", "newName");
  }

  private RenameAuditEvent createInvalidRenameEvent() {
    return new RenameAuditEvent(user, null, "oldName", null);
  }
}
