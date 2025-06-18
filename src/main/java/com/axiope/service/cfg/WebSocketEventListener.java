package com.axiope.service.cfg;

import com.researchspace.service.IMessageAndNotificationTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Component
public class WebSocketEventListener {
  @Autowired private IMessageAndNotificationTracker messageAndNotificationTracker;

  @EventListener
  public void handleSubscribeEvent(SessionSubscribeEvent event) {
    // extract userID from topic header in the form of /topic/notifications/{userID}
    String topic = event.getMessage().getHeaders().get("simpDestination").toString();
    String userID = topic.substring(topic.lastIndexOf('/') + 1);
    messageAndNotificationTracker.sendNotificationUpdate(Long.valueOf(userID));
  }
}
