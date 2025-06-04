package com.researchspace.webapp.messaging;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class NotificationController {

  private final SimpMessagingTemplate messagingTemplate;

  public NotificationController(
      @Autowired(required = false) SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void sendNotificationUpdate(Long userId, NotificationMessage notification) {
    if (messagingTemplate != null) {
      messagingTemplate.convertAndSend("/topic/notifications/" + userId, notification);
    }
  }
}
