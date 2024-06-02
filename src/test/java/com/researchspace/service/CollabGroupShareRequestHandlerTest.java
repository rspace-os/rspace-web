package com.researchspace.service;

import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class CollabGroupShareRequestHandlerTest extends SpringTransactionalTest {

  @Autowired()
  @Qualifier("collabShareRequestHandler")
  private RSpaceRequestUpdateHandler handler;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testHandleRequest() {
    assertTrue(handler.handleRequest(MessageType.REQUEST_EXTERNAL_SHARE));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testHandleMessageOrRequestSetUp() {

    User u = TestFactory.createAnyUser("any");
    CommunicationTarget ct = new CommunicationTarget();
    ct.setCommunication(new Notification());
    // doesn't work iwth notifications
    handler.handleMessageOrRequestUpdate(ct, u);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testHandleMessageOrRequestSetUpThrowsIAEIfWrongMEssageType() {
    MessageOrRequest mor = new MessageOrRequest(MessageType.REQUEST_RECORD_REVIEW);
    User u = TestFactory.createAnyUser("any");
    CommunicationTarget ct = new CommunicationTarget();
    ct.setCommunication(mor);
    // doesn't work with wrong message type
    handler.handleMessageOrRequestUpdate(ct, u);
  }
}
