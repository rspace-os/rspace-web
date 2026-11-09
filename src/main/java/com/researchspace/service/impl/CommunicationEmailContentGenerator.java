package com.researchspace.service.impl;

import com.researchspace.booking.service.BookingNotificationMessageFormatter;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.data.BookingNotificationData;
import com.researchspace.service.EmailContent;
import com.researchspace.service.NotificationTypeMessages;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.velocity.tools.generic.EscapeTool;

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
    if (communication instanceof Notification notification
        && BookingNotificationMessageFormatter.isBookingNotification(
            notification.getNotificationType())
        && notification.getNotificationDataObject() instanceof BookingNotificationData data) {
      Map<String, Object> model = velocityModelFor(communication);
      model.put(
          "bookingTimestamp",
          BookingNotificationMessageFormatter.formatEmailTimestamp(
              Instant.now(), data, contentGenerator.getMessages(), contentGenerator.getLocale()));
      return contentGenerator.render(
          bookingSubjectKey(notification.getNotificationType()),
          new Object[] {data.getInstrumentName()},
          "notification.vm",
          model);
    }
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

  private static String bookingSubjectKey(NotificationType type) {
    return switch (type) {
      case NOTIFICATION_BOOKING_CREATED -> "notificationType.bookingCreatedSubject";
      case NOTIFICATION_BOOKING_CANCELLED -> "notificationType.bookingCancelledSubject";
      case NOTIFICATION_BOOKING_RESTORED -> "notificationType.bookingRestoredSubject";
      case NOTIFICATION_BOOKING_CHANGED -> "notificationType.bookingChangedSubject";
      default -> throw new IllegalArgumentException("Not a booking notification: " + type);
    };
  }

  private Map<String, Object> velocityModelFor(Communication communication) {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", communication);
    model.put("esc", new EscapeTool());
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
