package com.researchspace.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.comms.MessageType;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class JoinGroupRequestHandlerTest extends SpringTransactionalTest {

  @Autowired()
  @Qualifier("joinGroupRequestHandler")
  private RSpaceRequestUpdateHandler handler;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testHandleRequest() {
    assertTrue(handler.handleRequest(MessageType.REQUEST_JOIN_LAB_GROUP));
    assertFalse(handler.handleRequest(MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP));
  }
}
