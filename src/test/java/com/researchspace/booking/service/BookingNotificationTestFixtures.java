package com.researchspace.booking.service;

import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.service.CommunicationManager;
import java.util.List;

final class BookingNotificationTestFixtures {

  private BookingNotificationTestFixtures() {}

  static List<Notification> notificationsFor(
      CommunicationManager communicationManager, User user, NotificationType type) {
    return communicationManager
        .getNewNotificationsForUser(
            user.getUsername(), PaginationCriteria.createDefaultForClass(CommunicationTarget.class))
        .getResults()
        .stream()
        .filter(notification -> notification.getNotificationType() == type)
        .toList();
  }

  static final class ForcedRollback extends RuntimeException {}
}
