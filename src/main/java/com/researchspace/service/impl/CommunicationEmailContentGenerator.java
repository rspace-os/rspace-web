package com.researchspace.service.impl;

import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.service.EmailContent;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.velocity.tools.generic.DateTool;

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
    String templateName = templateFor(communication);
    return templateName == null
        ? null
        : contentGenerator.generatePlainTextAndHtmlContent(
            templateName, velocityModelFor(communication));
  }

  private String templateFor(Communication communication) {
    if (communication.isNotification()) {
      return "notification.vm";
    }
    if (communication instanceof MessageOrRequest messageOrRequest) {
      MessageType messageType = messageOrRequest.getMessageType();
      return MessageType.SIMPLE_MESSAGE.equals(messageType) ? "message.vm" : "request.vm";
    }
    return null;
  }

  private Map<String, Object> velocityModelFor(Communication communication) {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", communication);
    model.put("baseURL", htmlDomainPrefix);
    model.put("dateOb", new Date());
    model.put("date", new DateTool());
    return model;
  }
}
