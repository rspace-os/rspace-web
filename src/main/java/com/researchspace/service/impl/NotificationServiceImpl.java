package com.researchspace.service.impl;

import com.researchspace.service.NotificationService;
import com.researchspace.webapp.messaging.NotificationMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {

  @Autowired private SimpMessagingTemplate messagingTemplate;

  @Override
  public void sendNotificationUpdate(Long userId, NotificationMessage notification) {
    if (messagingTemplate != null) {
      messagingTemplate.convertAndSend("/topic/notifications/" + userId, notification);
    }
  }
}
