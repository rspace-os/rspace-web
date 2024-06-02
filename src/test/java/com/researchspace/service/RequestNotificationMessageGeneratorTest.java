package com.researchspace.service;

import static org.junit.Assert.assertNotNull;

import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.record.Record;
import com.researchspace.service.impl.RequestNotificationMessageGenerator;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RequestNotificationMessageGeneratorTest extends SpringTransactionalTest {

  @Autowired private RequestNotificationMessageGenerator msGenerator;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testUpdateStatus() {
    User u = createAndSaveUserIfNotExists("anyuser");
    MessageOrRequest mor = new MessageOrRequest(MessageType.REQUEST_EXTERNAL_SHARE);

    assertNotNull(
        msGenerator.generateMsgOnRequestStatusUpdate(
            u, CommunicationStatus.COMPLETED, CommunicationStatus.NEW, mor));
    assertNotNull(
        msGenerator.generateMsgOnRequestStatusUpdate(
            u, CommunicationStatus.REJECTED, CommunicationStatus.NEW, mor));
  }

  @Test
  public void testUpdateStatusOfWitnessREquest() throws Exception {
    User u = createAndSaveUserIfNotExists("anyuser");
    initialiseContentWithEmptyContent(u);
    Record record = createBasicDocumentInRootFolderWithText(u, "any");

    MessageOrRequest mor = new MessageOrRequest(MessageType.REQUEST_RECORD_WITNESS);
    mor.setRecord(record);

    assertNotNull(
        msGenerator.generateMsgOnRequestStatusUpdate(
            u, CommunicationStatus.COMPLETED, CommunicationStatus.NEW, mor));
    assertNotNull(
        msGenerator.generateMsgOnRequestStatusUpdate(
            u, CommunicationStatus.REJECTED, CommunicationStatus.NEW, mor));
  }

  @Test
  public void testUpdateStatusOfLabGroup() throws Exception {
    User u = createAndSaveUserIfNotExists("anyuser");
    initialiseContentWithEmptyContent(u);

    MessageOrRequest mor = new MessageOrRequest(MessageType.REQUEST_JOIN_LAB_GROUP);

    assertNotNull(
        msGenerator.generateMsgOnRequestStatusUpdate(
            u, CommunicationStatus.COMPLETED, CommunicationStatus.NEW, mor));
    assertNotNull(
        msGenerator.generateMsgOnRequestStatusUpdate(
            u, CommunicationStatus.REJECTED, CommunicationStatus.NEW, mor));
  }
}
