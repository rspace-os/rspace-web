package com.researchspace.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class NotificationController {

  private final SimpMessagingTemplate messagingTemplate;

  public void sendNotificationUpdate(Long userId, Message notification) {
    messagingTemplate.convertAndSend("/topic/notifications/" + userId, notification);
  }
}
