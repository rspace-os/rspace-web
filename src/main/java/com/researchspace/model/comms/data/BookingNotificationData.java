package com.researchspace.model.comms.data;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Booking details retained so each notification recipient can format the booked interval. */
@Data
@EqualsAndHashCode(callSuper = false)
public class BookingNotificationData extends NotificationData {

  private String bookingId;
  private String instrumentName;
  private String instrumentGlobalIdentifier;
  private String startTime;
  private String endTime;
}
