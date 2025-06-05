package com.researchspace.service;

import com.researchspace.webapp.messaging.NotificationMessage;

/** Service for sending notifications to users via WebSocket */
public interface NotificationService {

  /**
   * Send a notification update to a specific user
   *
   * @param userId the user ID to send the notification to
   * @param notification the notification message to send
   */
  void sendNotificationUpdate(Long userId, NotificationMessage notification);
}
