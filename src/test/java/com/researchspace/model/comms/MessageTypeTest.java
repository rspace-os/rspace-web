package com.researchspace.model.comms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MessageTypeTest {

  @BeforeAll
  public static void setUpBeforeClass() throws Exception {}

  @Test
  public void isStandardType() {
    assertTrue(MessageType.SIMPLE_MESSAGE.isStandardType());
    assertFalse(MessageType.REQUEST_CREATE_LAB_GROUP.isStandardType());
    assertFalse(MessageType.REQUEST_SHARE_RECORD.isStandardType());
    assertFalse(MessageType.REQUEST_JOIN_LAB_GROUP.isStandardType());
  }
}
