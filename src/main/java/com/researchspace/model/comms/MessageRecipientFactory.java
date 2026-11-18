package com.researchspace.model.comms;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/** Sets an email's To and Bcc recipients for a communication. */
public class MessageRecipientFactory {

  /**
   * A single recipient goes in To. Several recipients go in Bcc so they don't see each other's
   * addresses, with one To address: the communication's originator, or {@code fallbackTo} (the
   * sending account) when there is none. Global messages always use Bcc, even for one recipient.
   *
   * @param addrs recipient addresses for this email
   * @param message the message whose recipients are set
   * @param comm the communication being sent, or null for a system email
   * @param fallbackTo the To address when {@code comm} has no originator email
   */
  public void populateRecipients(
      List<String> addrs, MimeMessage message, Communication comm, String fallbackTo)
      throws MessagingException, AddressException {
    if (addrs.size() == 1 && !isGlobalMessage(comm)) {
      message.addRecipient(Message.RecipientType.TO, new InternetAddress(addrs.get(0)));
      return;
    }
    String to = originatorEmail(comm);
    if (StringUtils.isBlank(to)) {
      to = fallbackTo;
    }
    message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
    for (String bcc : addrs) {
      // the To address already receives the email
      if (!bcc.equalsIgnoreCase(to)) {
        message.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc));
      }
    }
  }

  private String originatorEmail(Communication comm) {
    return comm != null && comm.getOriginator() != null ? comm.getOriginator().getEmail() : null;
  }

  private boolean isGlobalMessage(Communication comm) {
    return comm != null
        && comm.isMessageOrRequest()
        && ((MessageOrRequest) comm).getMessageType().equals(MessageType.GLOBAL_MESSAGE);
  }
}
