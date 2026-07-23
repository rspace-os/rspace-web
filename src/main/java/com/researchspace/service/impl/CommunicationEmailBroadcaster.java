package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.Broadcaster;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
import java.util.List;

/** Renders RSpace communications and broadcasts them to recipients who opted into email. */
public class CommunicationEmailBroadcaster implements Broadcaster {

  private final EmailBroadcast emailSender;
  private final CommunicationEmailContentGenerator contentGenerator;

  public CommunicationEmailBroadcaster(
      EmailBroadcast emailSender,
      EmailContentGenerator emailContentGenerator,
      String htmlDomainPrefix) {
    this.emailSender = emailSender;
    this.contentGenerator =
        new CommunicationEmailContentGenerator(emailContentGenerator, htmlDomainPrefix);
  }

  @Override
  public void broadcast(Communication communication) {
    List<String> recipients =
        communication.getRecipients().stream()
            .map(target -> target.getRecipient())
            .filter(User::isEnabled)
            .filter(recipient -> wantsEmail(communication, recipient))
            .map(User::getEmail)
            .toList();
    if (recipients.isEmpty()) {
      return;
    }

    EmailContent content = contentGenerator.generate(communication);
    emailSender.sendEmail(content, recipients, communication);
  }

  private boolean wantsEmail(Communication communication, User recipient) {
    Preference preference =
        communication.isNotification()
            ? Preference.BROADCAST_NOTIFICATIONS_BY_EMAIL
            : communication.isMessageOrRequest() ? Preference.BROADCAST_REQUEST_BY_EMAIL : null;
    return preference != null && recipient.getValueForPreference(preference).getValueAsBoolean();
  }
}
