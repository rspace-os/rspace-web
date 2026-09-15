package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.comms.MessageType;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class JoinGroupRequestHandlerTest extends SpringTransactionalTest {

  @Autowired()
  @Qualifier("joinGroupRequestHandler")
  private RSpaceRequestUpdateHandler handler;

  @Test
  public void testHandleRequest() {
    assertTrue(handler.handleRequest(MessageType.REQUEST_JOIN_LAB_GROUP));
    assertFalse(handler.handleRequest(MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP));
  }
}
