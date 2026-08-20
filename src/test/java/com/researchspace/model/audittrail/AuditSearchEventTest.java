package com.researchspace.model.audittrail;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.core.util.BasicPaginationCriteria;
import com.researchspace.core.util.IPagination;
import com.researchspace.model.core.Person;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AuditSearchEventTest {

  AuditSearchEvent srchEvent;
  Person user;
  IPagination<AuditSearchEvent> pgCrit;
  Object input;

  @BeforeEach
  public void setUp() {
    user = TestFactory.createAnyUser("any");
    pgCrit = BasicPaginationCriteria.createDefaultForClass(AuditSearchEvent.class);
    srchEvent = new AuditSearchEvent(user, input, pgCrit);
    input = new Object();
  }

  @Test
  public void testAuditSearchEvent() {
    srchEvent = new AuditSearchEvent(user, input, pgCrit);
    Assertions.assertNotNull(srchEvent.getAuditAction());
    Assertions.assertEquals(AuditAction.SEARCH, srchEvent.getAuditAction());
  }

  @Test
  public void testAuditSearchEventThrowsIAEIfPagCritIsNull() {
    assertThrows(IllegalArgumentException.class, () -> new AuditSearchEvent(user, input, null));
  }

}
