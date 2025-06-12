package com.researchspace.service;

import com.researchspace.webapp.messaging.NotificationMessage;



public interface NotificationService {

  /**
   * Send a notification update to a specific user
   *
   * @param userId the user ID to send the notification to
   * @param notification the notification message to send
   */
  void sendNotificationUpdate(Long userId, NotificationMessage notification);
}
