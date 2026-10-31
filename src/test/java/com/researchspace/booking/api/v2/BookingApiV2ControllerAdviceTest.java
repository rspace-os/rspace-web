package com.researchspace.booking.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.booking.service.*;
import com.researchspace.service.MessageSourceUtils;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BookingApiV2ControllerAdviceTest {
  private final StaticMessageSource source = new StaticMessageSource();
  private final BookingApiV2ControllerAdvice advice =
      new BookingApiV2ControllerAdvice(new MessageSourceUtils(source));

  @Test
  void scheduling() {
    for (var reason : InvalidBookingSchedulingSettingsException.Reason.values()) {
      register(reason.errorCode());
      check(
          advice.handleInvalidBookingSettings(
              new InvalidBookingSchedulingSettingsException(reason)),
          HttpStatus.BAD_REQUEST,
          reason.errorCode());
    }
  }

  @Test
  void display() {
    for (var reason : InvalidBookingDisplaySettingsException.Reason.values()) {
      register(reason.errorCode());
      check(
          advice.handleInvalidBookingDisplaySettings(
              new InvalidBookingDisplaySettingsException(reason)),
          HttpStatus.BAD_REQUEST,
          reason.errorCode());
    }
  }

  @Test
  void sharing() {
    String key = "errors.api.v2.bookingConfiguration.defaultSharing.invalid";
    register(key);
    check(advice.handleInvalidBookingDefaultSharing(), HttpStatus.BAD_REQUEST, key);
  }

  @Test
  void policy() {
    for (var reason : BookingPolicyException.Reason.values()) {
      if (reason == BookingPolicyException.Reason.MAXIMUM_DURATION) continue;
      register(reason.errorCode());
      check(
          advice.handleBookingPolicy(policyFailure(reason)),
          HttpStatus.BAD_REQUEST,
          reason.errorCode());
    }
  }

  @Test
  void lifecycle() {
    String key = BookingConfigurationLifecycleException.MESSAGE_KEY;
    register(key);
    check(advice.handleBookingConfigurationLifecycle(), HttpStatus.CONFLICT, key);
  }

  @Test
  void stale() {
    String key = "errors.api.v2.bookingConfiguration.stale";
    register(key);
    check(advice.handleStaleBookingSettings(), HttpStatus.PRECONDITION_FAILED, key);
  }

  @Test
  void genericResourceErrorsPreserveSchedulingAndLifecycleBodies() {
    var operations =
        new BookingConfigurationResourceOperations(
            org.mockito.Mockito.mock(BookingConfigurationManager.class),
            org.mockito.Mockito.mock(
                com.researchspace.booking.service.BookingLocationFilterManager.class),
            org.mockito.Mockito.mock(BookingConfigurationProtectedResourceAccess.class),
            org.mockito.Mockito.mock(com.researchspace.service.FeatureFlagManager.class),
            java.time.Clock.systemUTC(),
            com.researchspace.model.booking.ApiV2BookingConfigurationResource.DESCRIPTION);
    var spec = operations.bookingConfigurationApiV2Resource();
    var shared =
        new com.researchspace.api.v2.controller.ApiV2ControllerAdvice(
            new MessageSourceUtils(source));
    for (var operation :
        java.util.List.of(
            com.researchspace.api.v2.resource.ResourceOperation.CREATE,
            com.researchspace.api.v2.resource.ResourceOperation.UPDATE,
            com.researchspace.api.v2.resource.ResourceOperation.BULK_CREATE,
            com.researchspace.api.v2.resource.ResourceOperation.BULK_UPDATE)) {
      for (var reason : InvalidBookingSchedulingSettingsException.Reason.values()) {
        register(reason.errorCode());
        var mapping =
            spec.errorMappings().get(operation).stream()
                .filter(
                    error ->
                        error.exceptionType() == InvalidBookingSchedulingSettingsException.class)
                .findFirst()
                .orElseThrow();
        check(
            shared.handleResourceException(
                mapping.translate(new InvalidBookingSchedulingSettingsException(reason))),
            HttpStatus.BAD_REQUEST,
            reason.errorCode());
      }
    }
    register(BookingConfigurationLifecycleException.MESSAGE_KEY);
    var lifecycle =
        spec
            .errorMappings()
            .get(com.researchspace.api.v2.resource.ResourceOperation.UPDATE)
            .stream()
            .filter(error -> error.exceptionType() == BookingConfigurationLifecycleException.class)
            .findFirst()
            .orElseThrow();
    check(
        shared.handleResourceException(
            lifecycle.translate(new BookingConfigurationLifecycleException())),
        HttpStatus.CONFLICT,
        BookingConfigurationLifecycleException.MESSAGE_KEY);
  }

  @Test
  void genericEventOperationsPreservePolicyBodies() {
    var operations =
        new TimeSlotBookingResourceOperations(
            org.mockito.Mockito.mock(TimeSlotBookingManager.class),
            org.mockito.Mockito.mock(com.researchspace.service.FeatureFlagManager.class),
            com.researchspace.model.booking.ApiV2TimeSlotBookingResource.DESCRIPTION,
            org.mockito.Mockito.mock(com.researchspace.service.MessageSourceUtils.class),
            org.mockito.Mockito.mock(
                com.researchspace.booking.service.BookingLocationFilterManager.class));
    var spec = operations.timeSlotBookingApiV2Resource();
    var shared =
        new com.researchspace.api.v2.controller.ApiV2ControllerAdvice(
            new MessageSourceUtils(source));
    for (var operation :
        java.util.List.of(
            com.researchspace.api.v2.resource.ResourceOperation.CREATE,
            com.researchspace.api.v2.resource.ResourceOperation.UPDATE)) {
      for (var reason : BookingPolicyException.Reason.values()) {
        if (reason == BookingPolicyException.Reason.MAXIMUM_DURATION) continue;
        register(reason.errorCode());
        var mapping =
            spec.errorMappings().get(operation).stream()
                .filter(error -> error.exceptionType() == BookingPolicyException.class)
                .findFirst()
                .orElseThrow();
        check(
            shared.handleResourceException(mapping.translate(policyFailure(reason))),
            HttpStatus.BAD_REQUEST,
            reason.errorCode());
      }
    }
  }

  @Test
  void calendarFailuresPreserveErrorBodies() {
    register("errors.api.v2.notFound");
    check(advice.handleCalendarNotFound(), HttpStatus.NOT_FOUND, "errors.api.v2.notFound");
    String key = "errors.api.v2.bookingCalendar.subscriptionConflict";
    register(key);
    check(advice.handleUserSubscriptionConflict(), HttpStatus.PRECONDITION_FAILED, key);
  }

  private BookingPolicyException policyFailure(BookingPolicyException.Reason reason) {
    return reason == BookingPolicyException.Reason.DAY_END_MISSING
        ? BookingPolicyException.dayEndMissing(
            LocalTime.of(2, 30), LocalDate.of(2026, 3, 29), ZoneId.of("Europe/Berlin"))
        : new BookingPolicyException(reason);
  }

  private void register(String key) {
    source.addMessage(key, Locale.getDefault(), "Detail " + key);
  }

  private void check(ResponseEntity<ApiV2Problem> response, HttpStatus status, String key) {
    assertEquals(ApiV2Problem.response(status, "Detail " + key, key, "Detail " + key), response);
  }
}
