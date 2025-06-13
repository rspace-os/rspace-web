package com.researchspace.service.impl;

import com.researchspace.service.NotificationService;
import com.researchspace.webapp.messaging.NotificationMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/*
 * Implementation of {@link NotificationService} that uses Spring's SimpMessagingTemplate to publish updated
 * notifications to a topic for a specific user.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

  private final SimpMessagingTemplate messagingTemplate;

  public NotificationServiceImpl(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @Override
  public void sendNotificationUpdate(Long userId, NotificationMessage notification) {
    messagingTemplate.convertAndSend("/topic/notifications/" + userId, notification);
  }
}
