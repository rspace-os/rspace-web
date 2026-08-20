package com.researchspace.model.audittrail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.core.Person;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class DuplicateAuditEventTest {

  private static final String DOCUMENT_ID = AuditTrailTestObject.getOid();
  private Person user = TestFactory.createAnyUser("any");

  @Test
  public void testGetAuditActionThrowsIAEIfMapNUll() {
    assertThrows(IllegalArgumentException.class, this::getInvalidDuplicateEvent);
  }

  @Test
  public void testGetDescription() {
    DuplicateAuditEvent event = getValidDuplicateEvent();
    assertEquals(AuditAction.DUPLICATE, event.getAuditAction());
    assertEquals(user, event.getSubject());
    assertEquals(DOCUMENT_ID, event.getAuditData().getData().get("id"));
    assertEquals("sourceFilename", event.getAuditData().getData().get("name"));
    assertEquals("from: \"sourceFilename\" to: \"sourceFilename_Copy\"",
        event.getDescription());
  }

  private DuplicateAuditEvent getValidDuplicateEvent() {
    return new DuplicateAuditEvent(user, new HashMap<>(), new AuditTrailTestObject(),
        "sourceFilename","sourceFilename_Copy" );
  }

  private DuplicateAuditEvent getInvalidDuplicateEvent() {
    return new DuplicateAuditEvent(user, null, null,
        null, "_Copy");
  }
}
