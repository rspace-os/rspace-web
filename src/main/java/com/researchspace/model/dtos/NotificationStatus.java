package com.researchspace.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Utility Object to enclose communication information such as number notifications, messages and
 * active messages.
 */
@Data
@AllArgsConstructor
public class NotificationStatus {

  private int notificationCount = 0;
  private int messageCount = 0;
  private int specialMessageCount = 0;
}
