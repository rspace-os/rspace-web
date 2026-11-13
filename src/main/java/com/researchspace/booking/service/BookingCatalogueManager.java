package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfigurationCapabilities;
import com.researchspace.model.booking.BookingIncrementUnit;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.collection.ResourceRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Read-only, caller-relative catalogue of resources that can be discovered in Booking. */
public interface BookingCatalogueManager {

  /** Capability required by a caller that is selecting a catalogue item for an event. */
  enum Capability {
    CREATE_BOOKING,
    CREATE_BLOCKOUT
  }

  record Location(String globalId, String name) {}

  record Item(
      long configurationId,
      long configurationVersion,
      String targetType,
      long targetId,
      String globalId,
      String name,
      String timezone,
      long slotGranularityMinutes,
      BookingIncrementUnit incrementUnit,
      List<BookingOpeningHoursEntry> openingHours,
      long bufferBeforeMinutes,
      long bufferAfterMinutes,
      long maxBookingDurationMinutes,
      boolean allowDoubleBooking,
      @Schema(nullable = true) String effectiveRole,
      BookingConfigurationCapabilities capabilities,
      @Schema(nullable = true) Location location) {}

  record Facets(List<String> types) {

    public Facets {
      types = List.copyOf(types);
    }
  }

  record Page(List<Item> items, int page, int pageSize, long total, Facets facets) {

    public Page {
      items = List.copyOf(items);
    }
  }

  record LocationPage(List<Location> items, int page, int pageSize, long total) {

    public LocationPage {
      items = List.copyOf(items);
    }
  }

  /**
   * Finds one requested page, intersecting the typed filter with Booking and Inventory visibility.
   */
  Page search(
      String query,
      String targetGlobalId,
      ResourceRequest request,
      List<String> targetTypes,
      List<String> locationGlobalIds,
      Capability capability,
      int page,
      int limit,
      User caller);

  /** Calendar resources and distinct total under correlated visible-event filtering. */
  Page searchCalendar(
      String query,
      ResourceRequest items,
      ResourceRequest events,
      Instant start,
      Instant end,
      int page,
      int limit,
      User caller);

  /** Finds one page of exact immediate-parent locations visible in both domains. */
  LocationPage searchLocations(
      String query, List<String> targetTypes, int page, int limit, User caller);
}
