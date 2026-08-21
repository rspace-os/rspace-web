package com.researchspace.model.audittrail;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.core.Person;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MoveAuditEventTest {
  Person user = TestFactory.createAnyUser("any");

  @BeforeEach
  public void setUp() {}

  @AfterEach
  public void tearDown() {}

  @Test
  public void testGetAuditActionThrowsIAEIfMapNUll() {
    assertThrows(IllegalArgumentException.class, this::invalidMoveEvent);
  }

  @Test
  public void testGetOriginalToCopy() {
    MoveAuditEvent event = aMoveEvent();
    Assertions.assertEquals(AuditAction.MOVE, event.getAuditAction());
    AuditData auData = event.getAuditData();
  }

  private MoveAuditEvent aMoveEvent() {
    return new MoveAuditEvent(
        user, new AuditTrailTestObject(), new AuditTrailTestObject(), new AuditTrailTestObject());
  }

  private MoveAuditEvent invalidMoveEvent() {
    return new MoveAuditEvent(user, null, null, new AuditTrailTestObject());
  }
}
