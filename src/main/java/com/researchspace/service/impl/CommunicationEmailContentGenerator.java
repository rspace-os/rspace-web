package com.researchspace.service.impl;

import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.Notification;
import com.researchspace.service.EmailContent;
import com.researchspace.service.NotificationTypeMessages;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** Selects and renders the email content for a communication. */
class CommunicationEmailContentGenerator {

  private final EmailContentGenerator contentGenerator;
  private final String htmlDomainPrefix;

  CommunicationEmailContentGenerator(
      EmailContentGenerator contentGenerator, String htmlDomainPrefix) {
    this.contentGenerator = contentGenerator;
    this.htmlDomainPrefix = htmlDomainPrefix;
  }

  EmailContent generate(Communication communication) {
    if (communication.isNotification()) {
      return contentGenerator.render(
          "email.notification.subject", "notification.vm", velocityModelFor(communication));
    }
    if (communication instanceof MessageOrRequest messageOrRequest) {
      MessageType messageType = messageOrRequest.getMessageType();
      boolean simpleMessage = MessageType.SIMPLE_MESSAGE.equals(messageType);
      return contentGenerator.render(
          simpleMessage ? "email.message.subject" : "email.request.subject",
          simpleMessage ? "message.vm" : "request.vm",
          velocityModelFor(communication));
    }
    throw new IllegalArgumentException(
        "Unsupported communication type: " + communication.getClass().getName());
  }

  private Map<String, Object> velocityModelFor(Communication communication) {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", communication);
    model.put("baseURL", htmlDomainPrefix);
    model.put("dateOb", new Date());
    model.put("date", new LocaleAwareDateTool(contentGenerator.getLocale()));
    if (communication instanceof Notification notification) {
      model.put(
          "notificationTypeKey",
          NotificationTypeMessages.keyFor(notification.getNotificationType()));
    }
    return model;
  }
}
