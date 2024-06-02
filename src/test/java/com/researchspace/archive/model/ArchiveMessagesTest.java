package com.researchspace.archive.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.core.util.XMLReadWriteUtils;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommsTestUtils;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.export.MessageArchiveDataHandler;
import com.researchspace.testutils.ArchiveTestUtils;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import javax.xml.bind.JAXBException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ArchiveMessagesTest {

  private MessageArchiveDataHandler archiver;
  int counter = 0;

  @Before
  public void setUp() throws Exception {
    archiver = new MessageArchiveDataHandler();
  }

  @Rule public TemporaryFolder tempExportFolder = new TemporaryFolder();

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testMessagesXML() throws JAXBException, IOException, Exception {
    // check empty lists are handled OK
    ArchiveMessages originalMessages =
        archiver.generateMessagesXML(tempExportFolder.getRoot(), Collections.emptyList());
    assertEquals(0, originalMessages.getMessages().size());
    assertEquals(0, originalMessages.getUsernames().size());

    User sender = TestFactory.createAnyUser("sender");
    User target = TestFactory.createAnyUser("target");
    User target2 = TestFactory.createAnyUser("target2");
    MessageOrRequest mor = createSimpleMessage(sender, target, target2);

    // and create a reply, from target back to sender:
    MessageOrRequest reply = replyToMessage(sender, target, mor);
    // and send a reply, from target 2 as well
    MessageOrRequest reply2 = replyToMessage(sender, target2, mor);

    originalMessages =
        archiver.generateMessagesXML(
            tempExportFolder.getRoot(), TransformerUtils.toList(mor, reply, reply2));
    assertEquals(3, originalMessages.getUsernames().size());
    assertEquals(3, originalMessages.getMessages().size());

    File xml = new File(tempExportFolder.getRoot(), ExportImport.MESSAGES);
    File schema = new File(tempExportFolder.getRoot(), ExportImport.MESSAGES_SCHEMA);
    // now read back in from XML:
    ArchiveMessages fromXML = XMLReadWriteUtils.fromXML(xml, ArchiveMessages.class, schema, null);
    assertTrue(ArchiveTestUtils.areEquals(originalMessages, fromXML));
  }

  /**
   * @param originalSender the person who sent the original message, will be the target of the reply
   * @param responder The priginal recipient, who will be the sender of this reply
   * @param originalMsg
   * @return
   */
  private MessageOrRequest replyToMessage(
      User originalSender, User responder, MessageOrRequest originalMsg) {
    MessageOrRequest reply = CommsTestUtils.createSimpleMessage(responder);
    reply.setPreviousMessage(originalMsg);
    originalMsg.setNextMessage(reply);
    reply.setId(getNextId());
    CommunicationTarget ct2 = new CommunicationTarget();
    ct2.setCommunication(reply);
    ct2.setRecipient(originalSender);
    ct2.setId(getNextId());
    originalMsg.getRecipients().addAll(TransformerUtils.toSet(ct2));
    return reply;
  }

  private MessageOrRequest createSimpleMessage(User from, User... tos) {
    MessageOrRequest mor = CommsTestUtils.createSimpleMessage(from);
    mor.setId(1l);
    for (User u : tos) {
      CommunicationTarget ct = new CommunicationTarget();
      ct.setCommunication(mor);
      ct.setRecipient(u);
      ct.setId(getNextId());
      mor.getRecipients().addAll(TransformerUtils.toSet(ct));
    }

    return mor;
  }

  long getNextId() {
    return ++counter;
  }
}
