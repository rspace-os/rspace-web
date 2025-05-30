package com.researchspace.messaging;

import lombok.Data;

@Data
public class NotificationMessage {
  private final int notificationCount;
  private final int messageCount;
  private final int specialMessageCount;

  public NotificationMessage(int notificationCount, int messageCount, int specialMessageCount) {
    this.notificationCount = notificationCount;
    this.messageCount = messageCount;
    this.specialMessageCount = specialMessageCount;
  }
}
