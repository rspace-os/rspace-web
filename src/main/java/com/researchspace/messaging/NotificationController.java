package com.researchspace.messaging;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class NotificationController {

  private final SimpMessagingTemplate messagingTemplate;

  public NotificationController(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void sendNotificationUpdate(Long userId, NotificationMessage notification) {
    messagingTemplate.convertAndSend("/topic/notifications/" + userId, notification);
  }
}
