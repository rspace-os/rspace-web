package msgRecipientFac;

import static com.researchspace.core.util.TransformerUtils.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageRecipientFactory;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.record.TestFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MessageRecipientFactoryTest {
  MessageRecipientFactory fac = null;
  User sender = TestFactory.createAnyUser("u1");
  User recip1 = TestFactory.createAnyUser("u2");
  User recip2 = TestFactory.createAnyUser("u3");

  @Before
  public void setUp() throws Exception {
    fac = new MessageRecipientFactory();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testPopulateRecipientsForGlobalMsgSendsBCC()
      throws AddressException, MessagingException {
    MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));

    recip1.setEmail("a@b.com");
    recip2.setEmail("c@d.com");

    List<String> recipients = Arrays.asList(new String[] {recip1.getEmail(), recip2.getEmail()});
    MessageOrRequest global = setupMsgTo2People(MessageType.GLOBAL_MESSAGE);

    fac.populateRecipients(recipients, message, global);

    String[] bccs = message.getHeader("Bcc");
    assertEquals(1, bccs.length);
    assertTrue(bccs[0].contains(recip1.getEmail()));
    assertTrue(bccs[0].contains(recip2.getEmail()));
    String[] to = message.getHeader("To");
    assertTrue(to[0].contains(sender.getEmail()));
  }

  @Test
  public void testPopulateRecipientsForRegularMsgAllTo()
      throws AddressException, MessagingException {
    MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));

    recip1.setEmail("a@b.com");
    recip2.setEmail("c@d.com");

    List<String> recipients = Arrays.asList(new String[] {recip1.getEmail(), recip2.getEmail()});
    MessageOrRequest global = setupMsgTo2People(MessageType.SIMPLE_MESSAGE);

    fac.populateRecipients(recipients, message, global);

    String[] bccs = message.getHeader("Bcc");
    assertNull(bccs);
    String[] to = message.getHeader("To");
    assertTrue(to[0].contains(recip1.getEmail()));
    assertTrue(to[0].contains(recip2.getEmail()));

    assertFalse(to[0].contains(sender.getEmail()));
  }

  private MessageOrRequest setupMsgTo2People(MessageType type) {
    MessageOrRequest global = new MessageOrRequest(type);
    global.setOriginator(sender);
    CommunicationTarget ct1 = new CommunicationTarget();
    ct1.setCommunication(global);
    ct1.setRecipient(recip1);
    CommunicationTarget ct2 = new CommunicationTarget();
    ct2.setCommunication(global);
    ct2.setRecipient(recip2);
    global.setRecipients(toSet(ct1, ct2));
    return global;
  }
}
