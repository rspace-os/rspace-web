package com.researchspace.api.v2.controller;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.query.ApiV2ResourceRequestParser;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.booking.service.BookingCatalogueManager;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.FeatureFlagManager;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dedicated resource-discovery transport for Booking. */
@RestController
@RequestMapping("/api/v2/booking-catalogue")
public class BookingCatalogueController {

  private static final Duration MAX_AVAILABILITY_WINDOW = Duration.ofDays(2);

  private final BookingCatalogueManager manager;
  private final ApiV2ResourceCatalog resources;
  private final FeatureFlagManager featureFlags;

  public BookingCatalogueController(
      BookingCatalogueManager manager,
      ApiV2ResourceCatalog resources,
      FeatureFlagManager featureFlags) {
    this.manager = manager;
    this.resources = resources;
    this.featureFlags = featureFlags;
  }

  @GetMapping
  public BookingCatalogueManager.Page search(
      @RequestParam(name = "q", required = false) @Size(max = 255) String query,
      @RequestParam(required = false) String target,
      @RequestParam(required = false) String where,
      @RequestParam(name = "type", required = false) List<String> targetTypes,
      @RequestParam(name = "location", required = false) List<String> locations,
      @RequestParam(required = false) BookingCatalogueManager.Capability capability,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(required = false) String availability,
      @RequestParam(required = false) Instant availabilityStart,
      @RequestParam(required = false) Instant availabilityEnd,
      @RequestParam(required = false) Instant now,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabled(caller);
    BookingCatalogueManager.Availability category =
        availability == null
            ? null
            : BookingCatalogueManager.Availability.fromValue(availability)
                .orElseThrow(BookingCatalogueController::invalidAvailability);
    BookingCatalogueManager.AvailabilityWindow window =
        category == null ? null : window(availabilityStart, availabilityEnd, now);
    return manager.search(
        query,
        target,
        filtered(where, caller),
        targetTypes == null ? List.of() : targetTypes,
        locations == null ? List.of() : locations,
        capability,
        mine,
        category,
        window,
        page,
        limit,
        caller.subject());
  }

  /**
   * Counts the items in each availability quick filter under the same filters as {@link #search},
   * so a client needs no rows to label the filters.
   */
  @GetMapping("/availability-counts")
  public BookingCatalogueManager.AvailabilityCounts countAvailability(
      @RequestParam(name = "q", required = false) @Size(max = 255) String query,
      @RequestParam(required = false) String target,
      @RequestParam(required = false) String where,
      @RequestParam(name = "type", required = false) List<String> targetTypes,
      @RequestParam(name = "location", required = false) List<String> locations,
      @RequestParam(required = false) BookingCatalogueManager.Capability capability,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(required = false) Instant availabilityStart,
      @RequestParam(required = false) Instant availabilityEnd,
      @RequestParam(required = false) Instant now,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabled(caller);
    return manager.countAvailability(
        query,
        target,
        filtered(where, caller),
        targetTypes == null ? List.of() : targetTypes,
        locations == null ? List.of() : locations,
        capability,
        mine,
        window(availabilityStart, availabilityEnd, now),
        caller.subject());
  }

  @GetMapping("/locations")
  public BookingCatalogueManager.LocationPage searchLocations(
      @RequestParam(name = "q", required = false) @Size(max = 255) String query,
      @RequestParam(name = "type", required = false) List<String> targetTypes,
      @RequestParam(name = "globalId", required = false) @Size(max = 100) List<String> globalIds,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabled(caller);
    return manager.searchLocations(
        query,
        targetTypes == null ? List.of() : targetTypes,
        globalIds == null ? List.of() : globalIds,
        page,
        limit,
        caller.subject());
  }

  private ResourceRequest filtered(String where, ApiV2Caller caller) {
    ApiV2ResourceRegistration<?, ?> registration =
        resources.find("booking-configurations").orElseThrow();
    return ApiV2ResourceRequestParser.filtered(
        where,
        registration.description(),
        resources.registry(),
        registration.runtimeFieldContext(caller.subject(), resources::runtimeFieldsOf));
  }

  /**
   * The caller's availability window. At most two days long, which covers a display day across a
   * daylight-saving change; {@code now} defaults to the server clock.
   */
  private static BookingCatalogueManager.AvailabilityWindow window(
      Instant start, Instant end, Instant now) {
    if (start == null
        || end == null
        || end.isBefore(start)
        || Duration.between(start, end).compareTo(MAX_AVAILABILITY_WINDOW) > 0) {
      throw invalidAvailability();
    }
    return new BookingCatalogueManager.AvailabilityWindow(
        start, end, now == null ? Instant.now() : now);
  }

  private static ApiV2BadRequestException invalidAvailability() {
    return new ApiV2BadRequestException("errors.api.v2.bookingCatalogue.availability.invalid");
  }

  private void requireEnabled(ApiV2Caller caller) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, caller.subject())) {
      throw new NotFoundException();
    }
  }
}
