package com.researchspace.api.v2.controller;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.query.ApiV2ResourceRequestParser;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.booking.service.BookingCatalogueManager;
import com.researchspace.service.FeatureFlagManager;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.NotFoundException;
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
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabled(caller);
    ApiV2ResourceRegistration<?, ?> registration =
        resources.find("booking-configurations").orElseThrow();
    return manager.search(
        query,
        target,
        ApiV2ResourceRequestParser.filtered(
            where,
            registration.description(),
            resources.registry(),
            registration.runtimeFieldContext(caller.subject(), resources::runtimeFieldsOf)),
        targetTypes == null ? List.of() : targetTypes,
        locations == null ? List.of() : locations,
        capability,
        page,
        limit,
        caller.subject());
  }

  @GetMapping("/locations")
  public BookingCatalogueManager.LocationPage searchLocations(
      @RequestParam(name = "q", required = false) @Size(max = 255) String query,
      @RequestParam(name = "type", required = false) List<String> targetTypes,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabled(caller);
    return manager.searchLocations(
        query, targetTypes == null ? List.of() : targetTypes, page, limit, caller.subject());
  }

  private void requireEnabled(ApiV2Caller caller) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, caller.subject())) {
      throw new NotFoundException();
    }
  }
}
