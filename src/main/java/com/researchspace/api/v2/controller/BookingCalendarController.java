package com.researchspace.api.v2.controller;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.model.ApiV2FieldsetQuery;
import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.api.v2.query.ApiV2ResourceRequestParser;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.booking.service.BookingCalendarSearchManager;
import com.researchspace.booking.service.BookingCatalogueManager;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRenderer;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import com.researchspace.service.FeatureFlagManager;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Calendar-specific queries with one server-owned scope for event and resource paging. */
@RestController
@RequestMapping("/api/v2")
public class BookingCalendarController {
  private final BookingCatalogueManager catalogue;
  private final BookingCalendarSearchManager calendar;
  private final ApiV2ResourceCatalog resources;
  private final CollectionDescription<TimeSlotBooking> description;
  private final CollectionDescription<TimeSlotBooking> filterDescription;
  private final FeatureFlagManager featureFlags;

  public BookingCalendarController(
      BookingCatalogueManager catalogue,
      BookingCalendarSearchManager calendar,
      ApiV2ResourceCatalog resources,
      FeatureFlagManager featureFlags,
      @Qualifier(
              com.researchspace.booking.config.BookingResourceAccessConfiguration
                  .TIME_SLOT_BOOKING_DESCRIPTION)
          CollectionDescription<TimeSlotBooking> description) {
    this.catalogue = catalogue;
    this.calendar = calendar;
    this.resources = resources;
    this.featureFlags = featureFlags;
    this.description = description;
    this.filterDescription =
        ApiV2TimeSlotBookingResource.calendarFilterDescription(
            description.accessPolicy().readAccess());
  }

  @GetMapping("/booking-catalogue/calendar")
  public BookingCatalogueManager.Page items(
      @RequestParam Instant calendarStart,
      @RequestParam Instant calendarEnd,
      @RequestParam(required = false) String where,
      @RequestParam(required = false) String eventWhere,
      @RequestParam(required = false) @Size(max = 255) String q,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabled(caller);
    validateInterval(calendarStart, calendarEnd);
    return catalogue.searchCalendar(
        q,
        filtered("booking-configurations", where, caller),
        filteredCalendarEvents(eventWhere, new ApiV2FieldsetQuery(), caller),
        calendarStart,
        calendarEnd,
        page,
        limit,
        caller.subject());
  }

  @GetMapping("/booking-calendar/events")
  public ApiV2ListResult<Map<String, Object>> events(
      @RequestParam Instant start,
      @RequestParam Instant end,
      @RequestParam(required = false) String where,
      @RequestParam(required = false) @Size(max = 255) String q,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit,
      @ModelAttribute("fieldsets") ApiV2FieldsetQuery fieldsets,
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller) {
    requireEnabled(caller);
    validateInterval(start, end);
    ResourceRequest parsed = filteredCalendarEvents(where, fieldsets, caller);
    ResourceRequest request =
        new ResourceRequest(
            parsed.filter(),
            parsed.serverConstraint(),
            List.of(new Sort("start", true), new Sort("id", true)),
            new ResourceRequest.Page(page, limit),
            parsed.fieldSelections(),
            IncludeTree.toDepth(description, resources.registry(), 1),
            parsed.runtime());
    ResourcePage<TimeSlotBooking> result =
        calendar.events(request, start, end, q, caller.subject());
    Set<Object> targetIds =
        result.resources().stream()
            .map(TimeSlotBooking::getVisibleTarget)
            .filter(java.util.Objects::nonNull)
            .map(target -> (Object) target.id())
            .collect(Collectors.toSet());
    var targets =
        resources.relationshipTargets().stream()
            .filter(target -> target.description().resourceName().equals("booking-instruments"))
            .findFirst()
            .orElseThrow()
            .resolveReadable(targetIds, caller.subject());
    List<Map<String, Object>> documents =
        new ResourceRenderer(resources.registry())
            .renderAll(
                result.resources(),
                description,
                ignored -> request.fields(),
                request.fieldSelections(),
                request.includes(),
                (name, id) ->
                    name.equals("booking-instruments")
                        ? Optional.ofNullable(targets.get(id))
                        : Optional.empty());
    return ApiV2ListResult.of(documents, result.total(), limit, page);
  }

  private ResourceRequest filtered(
      String resource, String where, ApiV2FieldsetQuery fieldsets, ApiV2Caller caller) {
    var registration = resources.find(resource).orElseThrow();
    return ApiV2ResourceRequestParser.filtered(
        where,
        fieldsets,
        registration.description(),
        resources.registry(),
        registration.runtimeFieldContext(caller.subject(), resources::runtimeFieldsOf));
  }

  private ResourceRequest filteredCalendarEvents(
      String where, ApiV2FieldsetQuery fieldsets, ApiV2Caller caller) {
    var registration = resources.find("bookings").orElseThrow();
    var runtime = registration.runtimeFieldContext(caller.subject(), resources::runtimeFieldsOf);
    ResourceRequest filter =
        ApiV2ResourceRequestParser.filtered(
            where, filterDescription, resources.registry(), runtime);
    ResourceRequest projection =
        ApiV2ResourceRequestParser.filtered(
            null, fieldsets, description, resources.registry(), runtime);
    return new ResourceRequest(
        filter.filter(),
        List.of(),
        new ResourceRequest.Page(1, 1),
        projection.fieldSelections(),
        IncludeTree.empty(),
        filter.runtime());
  }

  private ResourceRequest filtered(String resource, String where, ApiV2Caller caller) {
    return filtered(resource, where, new ApiV2FieldsetQuery(), caller);
  }

  private void requireEnabled(ApiV2Caller caller) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, caller.subject())) {
      throw new NotFoundException();
    }
  }

  private static void validateInterval(Instant start, Instant end) {
    if (!start.isBefore(end)) throw new ApiV2BadRequestException("errors.api.v2.booking.window");
  }
}
