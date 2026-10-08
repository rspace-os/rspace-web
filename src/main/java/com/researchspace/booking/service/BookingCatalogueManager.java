package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookingOpeningException;
import com.researchspace.model.collection.ResourceRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
      String openingStart,
      String openingEnd,
      List<Integer> openDays,
      List<BookingOpeningException> openingExceptions,
      long bufferBeforeMinutes,
      long bufferAfterMinutes,
      long maxBookingDurationMinutes,
      boolean allowDoubleBooking,
      String effectiveRole,
      Map<String, Object> capabilities,
      Location location) {}

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

  /** An availability quick filter; an item is in at most one category. */
  enum Availability {
    /** Free at {@link AvailabilityWindow#now()}. */
    AVAILABLE_NOW("available-now"),
    /** Busy at {@link AvailabilityWindow#now()} and free again later in the window. */
    FREE_LATER_TODAY("free-later-today");

    private final String value;

    Availability(String value) {
      this.value = value;
    }

    /** The public request value. */
    public String value() {
      return value;
    }

    /** The category a public request value names, if any. */
    public static Optional<Availability> fromValue(String value) {
      return Arrays.stream(values()).filter(category -> category.value.equals(value)).findFirst();
    }
  }

  /**
   * The caller's availability window for today, {@code [start, end)}, and the instant to classify.
   * The client derives the window from its display time zone and availability-window preference, so
   * the server applies no display wall-clock rule of its own. Opening hours still use each item's
   * scheduling time zone.
   */
  record AvailabilityWindow(Instant start, Instant end, Instant now) {

    public AvailabilityWindow {
      Objects.requireNonNull(start, "Window start");
      Objects.requireNonNull(end, "Window end");
      Objects.requireNonNull(now, "Now");
    }
  }

  /** How many matching items are in each availability category. */
  record AvailabilityCounts(long availableNow, long freeLaterToday) {}

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
      boolean ownedByCaller,
      int page,
      int limit,
      User caller);

  /**
   * As {@link #search(String, String, ResourceRequest, List, List, Capability, boolean, int, int,
   * User)}, keeping only the items in the {@code availability} category of {@code window}. Rows and
   * the total agree, and the items keep catalogue order. A null {@code availability} applies no
   * availability rule.
   *
   * <p>An item is free where the window lies inside its opening hours, evaluated per date in its
   * scheduling time zone, and outside every confirmed event widened by the item's buffers.
   * Maintenance always counts as busy; bookings do unless the item allows double booking.
   */
  Page search(
      String query,
      String targetGlobalId,
      ResourceRequest request,
      List<String> targetTypes,
      List<String> locationGlobalIds,
      Capability capability,
      boolean ownedByCaller,
      Availability availability,
      AvailabilityWindow window,
      int page,
      int limit,
      User caller);

  /**
   * Counts the items the same catalogue filters match in each availability category of {@code
   * window}, without returning rows. Only items the caller may read are counted.
   */
  AvailabilityCounts countAvailability(
      String query,
      String targetGlobalId,
      ResourceRequest request,
      List<String> targetTypes,
      List<String> locationGlobalIds,
      Capability capability,
      boolean ownedByCaller,
      AvailabilityWindow window,
      User caller);

  /** Calendar resources and distinct total under correlated visible-event filtering. */
  Page searchCalendar(
      String query,
      ResourceRequest items,
      ResourceRequest events,
      java.time.Instant start,
      java.time.Instant end,
      boolean ownedByCaller,
      int page,
      int limit,
      User caller);

  /** Finds one page of exact immediate-parent locations visible in both domains. */
  default LocationPage searchLocations(
      String query, List<String> targetTypes, int page, int limit, User caller) {
    return searchLocations(query, targetTypes, List.of(), page, limit, caller);
  }

  /**
   * As {@link #searchLocations(String, List, int, int, User)}. A query that is a Container or
   * workbench global ID matches that location. Non-empty {@code globalIds} restrict the page to
   * those locations, for restoring saved filters; unreadable and unknown IDs are omitted alike.
   */
  LocationPage searchLocations(
      String query,
      List<String> targetTypes,
      List<String> globalIds,
      int page,
      int limit,
      User caller);
}
