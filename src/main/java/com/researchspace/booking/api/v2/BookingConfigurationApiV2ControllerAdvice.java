package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.booking.service.BookingConfigurationTargetConflictException;
import com.researchspace.booking.service.InvalidBookableTargetException;
import com.researchspace.service.MessageSourceUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** Maps booking-configuration collection failures to public REST API v2 errors. */
@ControllerAdvice(assignableTypes = ApiV2CrudController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BookingConfigurationApiV2ControllerAdvice {

  private final MessageSourceUtils messages;

  public BookingConfigurationApiV2ControllerAdvice(MessageSourceUtils messages) {
    this.messages = messages;
  }

  @ExceptionHandler(InvalidBookableTargetException.class)
  public ResponseEntity<ApiV2Problem> handleInvalidBookableTarget() {
    return problem(HttpStatus.BAD_REQUEST, "errors.api.v2.bookingConfiguration.target.invalid");
  }

  @ExceptionHandler(BookingConfigurationTargetConflictException.class)
  public ResponseEntity<ApiV2Problem> handleBookingConfigurationTargetConflict() {
    return problem(HttpStatus.CONFLICT, "errors.api.v2.bookingConfiguration.target.conflict");
  }

  private ResponseEntity<ApiV2Problem> problem(HttpStatus status, String key) {
    String detail = messages.getMessage(key);
    return ApiV2Problem.response(status, detail, key, detail);
  }
}
