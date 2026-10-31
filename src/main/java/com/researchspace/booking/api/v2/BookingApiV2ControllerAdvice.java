package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.controller.ApiV2ControllerAdvice;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.booking.service.BookingCalendarManagerImpl.BookingCalendarNotFoundException;
import com.researchspace.booking.service.BookingCalendarManagerImpl.UserSubscriptionConflictException;
import com.researchspace.booking.service.BookingConfigurationLifecycleException;
import com.researchspace.booking.service.BookingNotificationSubscriptionConflictException;
import com.researchspace.booking.service.BookingPolicyException;
import com.researchspace.booking.service.InvalidBookingDefaultSharingException;
import com.researchspace.booking.service.InvalidBookingDisplaySettingsException;
import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException;
import com.researchspace.booking.service.StaleBookingSettingsException;
import com.researchspace.service.MessageSourceUtils;
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

  public BookingApiV2ControllerAdvice(MessageSourceUtils messages) {
    super(messages);
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

  @ExceptionHandler(InvalidBookingDefaultSharingException.class)
  public ResponseEntity<ApiV2Problem> handleInvalidBookingDefaultSharing() {
    return problem(
        HttpStatus.BAD_REQUEST, "errors.api.v2.bookingConfiguration.defaultSharing.invalid");
  }

  @ExceptionHandler(BookingPolicyException.class)
  public ResponseEntity<ApiV2Problem> handleBookingPolicy(BookingPolicyException ex) {
    return problem(HttpStatus.BAD_REQUEST, ex.reason().errorCode());
  }

  @ExceptionHandler(BookingConfigurationLifecycleException.class)
  public ResponseEntity<ApiV2Problem> handleBookingConfigurationLifecycle() {
    return problem(HttpStatus.CONFLICT, BookingConfigurationLifecycleException.MESSAGE_KEY);
  }

  @ExceptionHandler(StaleBookingSettingsException.class)
  public ResponseEntity<ApiV2Problem> handleStaleBookingSettings() {
    return problem(HttpStatus.PRECONDITION_FAILED, "errors.api.v2.bookingConfiguration.stale");
  }

  @ExceptionHandler(BookingCalendarNotFoundException.class)
  public ResponseEntity<ApiV2Problem> handleCalendarNotFound() {
    return problem(HttpStatus.NOT_FOUND, "errors.api.v2.notFound");
  }

  @ExceptionHandler(UserSubscriptionConflictException.class)
  public ResponseEntity<ApiV2Problem> handleUserSubscriptionConflict() {
    return problem(HttpStatus.CONFLICT, "errors.api.v2.bookingCalendar.subscriptionConflict");
  }

  @ExceptionHandler(BookingNotificationSubscriptionConflictException.class)
  public ResponseEntity<ApiV2Problem> handleBookingNotificationSubscriptionConflict() {
    return problem(HttpStatus.CONFLICT, "errors.api.v2.bookingNotifications.subscriptionConflict");
  }

  private ResponseEntity<ApiV2Problem> problem(HttpStatus status, String messageKey) {
    String detail = messages.getMessage(messageKey);
    return ApiV2Problem.response(status, detail, messageKey, detail);
  }
}
