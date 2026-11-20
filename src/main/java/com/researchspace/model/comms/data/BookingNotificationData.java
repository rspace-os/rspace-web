package com.researchspace.model.comms.data;

import com.researchspace.model.booking.BookingTimeFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Booking details retained so each notification recipient can format the booked interval. */
@Data
@EqualsAndHashCode(callSuper = false)
public class BookingNotificationData extends NotificationData {
  private String emailDisplayTimezone;
  private BookingTimeFormat emailTimeFormat;

  private String bookingId;
  private String instrumentName;
  private String instrumentGlobalIdentifier;
  private String startTime;
  private String endTime;
  private String cancellationReason;

  /** A cancelled booking returned to its slot; formatted as "restored" under the created type. */
  private boolean restored;
}
