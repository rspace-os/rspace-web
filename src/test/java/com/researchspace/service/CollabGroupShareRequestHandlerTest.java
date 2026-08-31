package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.Notification;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class CollabGroupShareRequestHandlerTest extends SpringTransactionalTest {

  @Autowired()
  @Qualifier("collabShareRequestHandler")
  private RSpaceRequestUpdateHandler handler;

  @Test
  public void testHandleRequest() {
    assertTrue(handler.handleRequest(MessageType.REQUEST_EXTERNAL_SHARE));
  }

  @Test
  public void testHandleMessageOrRequestSetUp() {
    User u = TestFactory.createAnyUser("any");
    CommunicationTarget ct = new CommunicationTarget();
    ct.setCommunication(new Notification());
    assertThrows(IllegalArgumentException.class, () -> handler.handleMessageOrRequestUpdate(ct, u));
  }

  @Test
  public void testHandleMessageOrRequestSetUpThrowsIAEIfWrongMEssageType() {
    MessageOrRequest mor = new MessageOrRequest(MessageType.REQUEST_RECORD_REVIEW);
    User u = TestFactory.createAnyUser("any");
    CommunicationTarget ct = new CommunicationTarget();
    ct.setCommunication(mor);
    assertThrows(IllegalArgumentException.class, () -> handler.handleMessageOrRequestUpdate(ct, u));
  }
}
