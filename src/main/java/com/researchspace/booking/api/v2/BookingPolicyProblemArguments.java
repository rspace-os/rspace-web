package com.researchspace.booking.api.v2;

import com.researchspace.booking.service.BookingPolicyException;
import com.researchspace.service.MessageSourceUtils;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Message arguments of a {@link BookingPolicyException} problem detail, shared by the controller
 * advice and the booking resource's error mapping so both bodies are identical.
 */
final class BookingPolicyProblemArguments {

  private BookingPolicyProblemArguments() {}

  /**
   * The local end time, its date and the zone's name in {@code locale} for a missing day end; the
   * maximum in words for a maximum-duration failure; otherwise none.
   */
  static Object[] of(BookingPolicyException exception, MessageSourceUtils messages, Locale locale) {
    if (exception.missingDayEnd().isPresent()) {
      BookingPolicyException.MissingDayEnd missing = exception.missingDayEnd().get();
      return new Object[] {
        missing
            .time()
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)),
        missing
            .date()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)),
        missing.zone().getDisplayName(TextStyle.FULL, locale)
      };
    }
    return exception.maximumDurationMinutes().isEmpty()
        ? new Object[0]
        : new Object[] {
          BookingDurationWords.format(
              exception.maximumDurationMinutes().getAsLong(), messages, locale)
        };
  }
}
