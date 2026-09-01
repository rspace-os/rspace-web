package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.service.BookingCatalogueManager;
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

  public BookingCatalogueController(BookingCatalogueManager manager) {
    this.manager = manager;
  }

  @GetMapping
  public BookingCatalogueManager.Page search(
      @RequestParam(name = "q", required = false) @Size(max = 255) String query,
      @RequestParam(required = false) String target,
      @RequestParam(name = "type", required = false) List<String> targetTypes,
      @RequestParam(name = "location", required = false) List<String> locations,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    return manager.search(
        query,
        target,
        targetTypes == null ? List.of() : targetTypes,
        locations == null ? List.of() : locations,
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
    return manager.searchLocations(
        query, targetTypes == null ? List.of() : targetTypes, page, limit, caller.subject());
  }
}
