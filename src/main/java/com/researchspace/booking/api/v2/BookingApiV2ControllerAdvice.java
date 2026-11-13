package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.controller.ApiV2ControllerAdvice;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.api.v2.controller.ApiV2ProblemFactory;
import com.researchspace.booking.service.BookingConfigurationLifecycleException;
import com.researchspace.booking.service.BookingPolicyException;
import com.researchspace.booking.service.InvalidBookingDisplaySettingsException;
import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException;
import com.researchspace.booking.service.StaleBookingSettingsException;
import com.researchspace.service.MessageSourceUtils;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** Feature failures and the shared transport error contract for feature controllers. */
@ControllerAdvice(basePackages = "com.researchspace.booking.api.v2")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BookingApiV2ControllerAdvice extends ApiV2ControllerAdvice {
  private final MessageSourceUtils messages;

  public BookingApiV2ControllerAdvice(MessageSourceUtils messages, ApiV2ProblemFactory problems) {
    super(messages, problems);
    this.messages = messages;
  }

  @ExceptionHandler(InvalidBookingSchedulingSettingsException.class)
  public ResponseEntity<ApiV2Problem> handleInvalidBookingSettings(
      InvalidBookingSchedulingSettingsException ex) {
    return problem(HttpStatus.BAD_REQUEST, ex.reason().errorCode());
  }

  @ExceptionHandler(InvalidBookingDisplaySettingsException.class)
  public ResponseEntity<ApiV2Problem> handleInvalidBookingDisplaySettings(
      InvalidBookingDisplaySettingsException ex) {
    return problem(HttpStatus.BAD_REQUEST, ex.reason().errorCode());
  }

  @ExceptionHandler(BookingPolicyException.class)
  public ResponseEntity<ApiV2Problem> handleBookingPolicy(BookingPolicyException ex) {
    String code = ex.reason().errorCode();
    if (ex.maximumDurationMinutes().isEmpty()) {
      return problem(HttpStatus.BAD_REQUEST, code);
    }
    long maximumMinutes = ex.maximumDurationMinutes().getAsLong();
    Locale locale = LocaleContextHolder.getLocale();
    return problems.problem(
        HttpStatus.BAD_REQUEST,
        code,
        BookingProblem.maximumDuration(maximumMinutes),
        BookingDurationWords.format(maximumMinutes, messages, locale));
  }

  @ExceptionHandler(BookingConfigurationLifecycleException.class)
  public ResponseEntity<ApiV2Problem> handleBookingConfigurationLifecycle() {
    return problem(HttpStatus.CONFLICT, BookingConfigurationLifecycleException.MESSAGE_KEY);
  }

  @ExceptionHandler(StaleBookingSettingsException.class)
  public ResponseEntity<ApiV2Problem> handleStaleBookingSettings() {
    return problem(HttpStatus.PRECONDITION_FAILED, "errors.api.v2.bookingConfiguration.stale");
  }
}
