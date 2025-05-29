package com.researchspace.messaging;

import java.time.Instant;
import lombok.Data;

@Data
public class NotificationMessage {
  private final int notificationCount;
  private final int messageCount;
  private final Instant timestamp;

  public NotificationMessage(int notificationCount, int messageCount) {
    this.notificationCount = notificationCount;
    this.messageCount = messageCount;
    this.timestamp = Instant.now();
  }
}
