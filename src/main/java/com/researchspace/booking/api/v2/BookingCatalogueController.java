package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.query.ApiV2ResourceRequestParser;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.booking.service.BookingCatalogueManager;
import com.researchspace.service.FeatureFlagManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
  @Operation(
      operationId = "searchBookingCatalogue",
      summary = "Search the Booking catalogue",
      tags = "booking-catalogue",
      responses = {
        @ApiResponse(responseCode = "200", description = "Matching bookable instruments."),
        @ApiResponse(responseCode = "400"),
        @ApiResponse(responseCode = "401"),
        @ApiResponse(responseCode = "404", description = "Booking is unavailable."),
        @ApiResponse(responseCode = "429"),
        @ApiResponse(responseCode = "500")
      })
  public BookingCatalogueManager.Page search(
      @RequestParam(name = "q", required = false) @Size(max = 255) String query,
      @RequestParam(required = false) String target,
      @RequestParam(required = false) String where,
      @RequestParam(name = "type", required = false) List<String> targetTypes,
      @RequestParam(name = "location", required = false) List<String> locations,
      @RequestParam(required = false) BookingCatalogueManager.Capability capability,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    BookingControllerSupport.requireEnabled(featureFlags, caller.subject());
    ApiV2ResourceRegistration<?, ?> registration =
        resources.find("booking-configurations").orElseThrow();
    return manager.search(
        query,
        target,
        ApiV2ResourceRequestParser.filtered(
            where,
            registration.description(),
            resources.registry(),
            registration.runtimeFieldContext(caller.subject())),
        targetTypes == null ? List.of() : targetTypes,
        locations == null ? List.of() : locations,
        capability,
        mine,
        page,
        limit,
        caller.subject());
  }

  @GetMapping("/locations")
  @Operation(
      operationId = "searchBookingCatalogueLocations",
      summary = "Search Booking catalogue locations",
      tags = "booking-catalogue",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Locations containing bookable instruments."),
        @ApiResponse(responseCode = "400"),
        @ApiResponse(responseCode = "401"),
        @ApiResponse(responseCode = "404", description = "Booking is unavailable."),
        @ApiResponse(responseCode = "429"),
        @ApiResponse(responseCode = "500")
      })
  public BookingCatalogueManager.LocationPage searchLocations(
      @RequestParam(name = "q", required = false) @Size(max = 255) String query,
      @RequestParam(name = "type", required = false) List<String> targetTypes,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    BookingControllerSupport.requireEnabled(featureFlags, caller.subject());
    return manager.searchLocations(
        query, targetTypes == null ? List.of() : targetTypes, page, limit, caller.subject());
  }
}
