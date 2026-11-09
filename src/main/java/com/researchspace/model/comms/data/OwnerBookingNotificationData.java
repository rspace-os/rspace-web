package com.researchspace.model.comms.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Email context captured for the instrument owner when a booking notification is created. */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class OwnerBookingNotificationData extends NotificationData {
  private String instrumentName;
  private String displayTimezone;
}
