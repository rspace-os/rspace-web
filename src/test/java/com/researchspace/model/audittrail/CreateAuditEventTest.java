package com.researchspace.model.audittrail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.core.Person;
import org.junit.jupiter.api.Test;

public class CreateAuditEventTest {

  private static final String DOCUMENT_ID = AuditTrailTestObject.getOid();
  Person user = TestFactory.createAnyUser("any");

  @Test
  public void testGetAuditActionThrowsIAEIfMapNUll() {
    assertThrows(IllegalArgumentException.class, this::getInvalidCreateEvent);
  }

  @Test
  public void testGetNameAndGetDescription() {
    CreateAuditEvent event = getValidCreateEvent();
    assertEquals(AuditAction.CREATE, event.getAuditAction());
    assertEquals(user, event.getSubject());
    assertEquals(DOCUMENT_ID, event.getAuditData().getData().get("id"));
    assertEquals("filename", event.getAuditData().getData().get("name"));
    assertNull(event.getDescription());
  }

  private CreateAuditEvent getValidCreateEvent() {
    return new CreateAuditEvent(user, new AuditTrailTestObject(), "filename");
  }

  private CreateAuditEvent getInvalidCreateEvent() {
    return new CreateAuditEvent(user, null, null);
  }
}
