package com.researchspace.service.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.record.TestFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

// see RSPAC-302
public class AbstractRSpaceRequestHandlerTest {

  class TestHandler extends AbstractRSpaceRequestUpdateHandler {

    boolean completed = false;
    boolean rejected = false;

    @Override
    public boolean handleRequest(MessageType messageType) {
      return true; // don't care what is message type here
    }

    @Override
    protected void doHandleMessageOrRequestUpdateOnCompletion(
        CommunicationTarget updatedTarget, User subject) {
      completed = true;
    }

    @Override
    protected void doHandleMessageOrRequestUpdateOnRejection(
        CommunicationTarget updatedTarget, User subject) {
      rejected = true;
    }
  }

  TestHandler handlerTSS;
  AbstractRSpaceRequestUpdateHandler api;
  private User updater, originator;
  private CommunicationTarget target;

  @Before
  public void setUp() throws Exception {
    handlerTSS = new TestHandler();
    api = handlerTSS;
    updater = TestFactory.createAnyUser("any");
    originator = TestFactory.createAnyUser("originator");

    target = new CommunicationTarget();
    target.setRecipient(updater);
    MessageOrRequest mor = TestFactory.createAnyRequest(originator, null);
    mor.getRecipients().add(target);
    target.setCommunication(mor);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testDoHandleMessageOrRequestUpdateOnCompletion() {
    target.setStatus(CommunicationStatus.COMPLETED);
    api.handleMessageOrRequestUpdate(target, updater);
    assertTrue(handlerTSS.completed);
    assertFalse(handlerTSS.rejected);
  }

  @Test
  public void testDoHandleMessageOrRequestUpdateOnRejection() {
    target.setStatus(CommunicationStatus.REJECTED);
    api.handleMessageOrRequestUpdate(target, updater);
    assertFalse(handlerTSS.completed);
    assertTrue(handlerTSS.rejected);
  }
}
