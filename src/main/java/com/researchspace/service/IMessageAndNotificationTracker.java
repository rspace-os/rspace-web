package com.researchspace.service;

import com.researchspace.model.comms.MessageType;
import com.researchspace.model.dtos.NotificationStatus;

/** Tracks the number of unread notifications and messages */
public interface IMessageAndNotificationTracker {

  /**
   * Increments the number of user notifications
   *
   * @param userId
   * @param incrementAmount, can be -ve if decrementing.
   * @return the current notification count.
   */
  int changeUserNotificationCount(Long userId, Integer incrementAmount);

  /**
   * Sets notification count to 0.
   *
   * @param username
   */
  void clearUserNotificationCount(Long userId);

  /**
   * Boolean test as to whether user has new notifications.
   *
   * @param username
   * @return <code>true</code> if user has new notifications; false otherwise
   */
  Boolean userHasNewNotifications(Long userId);

  /**
   * Get the notification count for the supplied user
   *
   * @param userId
   * @return
   */
  Integer getNotificationCountFor(Long userId);

  /**
   * Boolean test as to whether user has active messages
   *
   * @param id
   * @return
   */
  Boolean userHasActiveMessages(Long id);

  /**
   * Get the unread message count for the supplied user
   *
   * @param id
   * @return
   */
  Integer getMessageCountFor(Long id);

  /**
   * Get the special message count for the supplied user
   *
   * @param userId
   * @return
   */
  Integer getSpecialMessageCountFor(Long userId);

  /**
   * Convenience method that adds to the correct user/ message collection depending on the message
   * type.
   *
   * @param id
   * @param increment
   * @param messageType
   */
  void updateMessageCount(Long id, int increment, MessageType messageType);

  /**
   * Method to init the number of notifications, messages and special messages.
   *
   * @param userId
   * @param notificationStatus
   */
  void initCount(Long userId, NotificationStatus notificationStatus);
}
