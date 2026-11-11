package com.researchspace.booking.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.researchspace.api.v2.controller.ApiV2ControllerAdvice;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.api.v2.controller.ApiV2ProblemFactory;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.booking.service.*;
import com.researchspace.booking.service.BookingCreateExecutor;
import com.researchspace.booking.service.BookingLocationFilterManager;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.MessageSourceUtils;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BookingApiV2ControllerAdviceTest {
  private final StaticMessageSource source = new StaticMessageSource();
  private final BookingApiV2ControllerAdvice advice =
      new BookingApiV2ControllerAdvice(
          new MessageSourceUtils(source), new ApiV2ProblemFactory(new MessageSourceUtils(source)));

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
            mock(BookingConfigurationManager.class),
            mock(BookingLocationFilterManager.class),
            mock(BookingConfigurationProtectedResourceAccess.class),
            mock(FeatureFlagManager.class),
            Clock.systemUTC(),
            ApiV2BookingConfigurationResource.DESCRIPTION);
    var spec = operations.bookingConfigurationApiV2Resource();
    var shared =
        new ApiV2ControllerAdvice(
            new MessageSourceUtils(source),
            new ApiV2ProblemFactory(new MessageSourceUtils(source)));
    for (var operation :
        List.of(
            ResourceOperation.CREATE,
            ResourceOperation.UPDATE,
            ResourceOperation.BULK_CREATE,
            ResourceOperation.BULK_UPDATE)) {
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
        spec.errorMappings().get(ResourceOperation.UPDATE).stream()
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
            mock(TimeSlotBookingManager.class),
            mock(FeatureFlagManager.class),
            ApiV2TimeSlotBookingResource.DESCRIPTION,
            mock(MessageSourceUtils.class),
            mock(BookingLocationFilterManager.class),
            mock(BookingCreateExecutor.class));
    var spec = operations.timeSlotBookingApiV2Resource();
    var shared =
        new ApiV2ControllerAdvice(
            new MessageSourceUtils(source),
            new ApiV2ProblemFactory(new MessageSourceUtils(source)));
    for (var operation : List.of(ResourceOperation.CREATE, ResourceOperation.UPDATE)) {
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
    source.addMessage(key + "Title", Locale.getDefault(), "Detail " + key);
  }

  private void check(ResponseEntity<ApiV2Problem> response, HttpStatus status, String key) {
    assertEquals(status, response.getStatusCode());
    assertEquals("Detail " + key, response.getBody().title());
    assertEquals(key, response.getBody().code());
    assertEquals(
        key.endsWith("dayEndMissing") ? "Detail " + key : null, response.getBody().detail());
  }
}
