package msgRecipientFac;

import static com.researchspace.core.util.TransformerUtils.toSet;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageRecipientFactory;
import com.researchspace.model.comms.MessageType;
import com.researchspace.testutils.TestFactory;
import jakarta.mail.Address;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageRecipientFactoryTest {
  private static final String FROM = "support@example.com";

  MessageRecipientFactory fac;
  MimeMessage message;
  User sender = TestFactory.createAnyUser("u1");
  User recip1 = TestFactory.createAnyUser("u2");
  User recip2 = TestFactory.createAnyUser("u3");

  @BeforeEach
  public void setUp() {
    fac = new MessageRecipientFactory();
    message = new MimeMessage(Session.getDefaultInstance(new Properties()));
    sender.setEmail("sender@example.com");
    recip1.setEmail("a@b.com");
    recip2.setEmail("c@d.com");
  }

  @Test
  public void globalMessageGoesToSenderWithRecipientsInBcc() throws MessagingException {
    fac.populateRecipients(
        List.of("a@b.com", "c@d.com"), message, msgTo2People(MessageType.GLOBAL_MESSAGE), FROM);

    assertRecipients(RecipientType.TO, "sender@example.com");
    assertRecipients(RecipientType.BCC, "a@b.com", "c@d.com");
  }

  @Test
  public void severalRecipientsGoInBccWithTheSenderInTo() throws MessagingException {
    fac.populateRecipients(
        List.of("a@b.com", "c@d.com"), message, msgTo2People(MessageType.SIMPLE_MESSAGE), FROM);

    // RFC 5322 allows at most one To header (RSDEV-554)
    assertArrayEquals(new String[] {"sender@example.com"}, message.getHeader("To"));
    assertRecipients(RecipientType.BCC, "a@b.com", "c@d.com");
  }

  @Test
  public void severalRecipientsWithoutACommunicationGoToTheSendingAccount()
      throws MessagingException {
    fac.populateRecipients(List.of("a@b.com", "c@d.com"), message, null, FROM);

    assertRecipients(RecipientType.TO, FROM);
    assertRecipients(RecipientType.BCC, "a@b.com", "c@d.com");
  }

  @Test
  public void senderAmongTheRecipientsIsNotAlsoInBcc() throws MessagingException {
    fac.populateRecipients(
        List.of("a@b.com", "SENDER@example.com"),
        message,
        msgTo2People(MessageType.SIMPLE_MESSAGE),
        FROM);

    assertRecipients(RecipientType.TO, "sender@example.com");
    assertRecipients(RecipientType.BCC, "a@b.com");
  }

  @Test
  public void singleRecipientStaysInTo() throws MessagingException {
    fac.populateRecipients(
        List.of("a@b.com"), message, msgTo2People(MessageType.SIMPLE_MESSAGE), FROM);

    assertRecipients(RecipientType.TO, "a@b.com");
    assertNull(message.getHeader("Bcc"));
  }

  private void assertRecipients(RecipientType type, String... expected) throws MessagingException {
    Address[] actual = message.getRecipients(type);
    assertArrayEquals(
        expected,
        actual == null
            ? new String[0]
            : Arrays.stream(actual)
                .map(a -> ((InternetAddress) a).getAddress())
                .toArray(String[]::new));
  }

  private MessageOrRequest msgTo2People(MessageType type) {
    MessageOrRequest msg = new MessageOrRequest(type);
    msg.setOriginator(sender);
    CommunicationTarget ct1 = new CommunicationTarget();
    ct1.setCommunication(msg);
    ct1.setRecipient(recip1);
    CommunicationTarget ct2 = new CommunicationTarget();
    ct2.setCommunication(msg);
    ct2.setRecipient(recip2);
    msg.setRecipients(toSet(ct1, ct2));
    return msg;
  }
}
