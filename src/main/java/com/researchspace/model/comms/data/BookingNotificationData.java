package com.researchspace.model.comms.data;

import com.researchspace.model.booking.BookingTimeFormat;
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
  private String previousStartTime;
  private String previousEndTime;
  private String cancellationReason;

  /** Recipient display snapshot used by the email header as well as its saved body. */
  private String emailDisplayTimezone;

  private BookingTimeFormat emailTimeFormat;

  /** Retained for older restored notifications saved under the created type. */
  private boolean restored;
}
