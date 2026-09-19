package com.researchspace.model.audittrail;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AuditDataTest {

  AuditData data;

  @Test
  public void testToString() {
    data = new AuditData();
    data.put("name", "value");
    Assertions.assertEquals("name=value", data.toString());
    data.put("name2", "value2");

    Assertions.assertEquals("name=value&name2=value2", data.toString());
  }
}
