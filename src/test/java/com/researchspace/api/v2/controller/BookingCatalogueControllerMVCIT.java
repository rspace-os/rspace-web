package com.researchspace.api.v2.controller;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.AbstractAppInitializor;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@ApiV2WebIntegrationTest
class BookingCatalogueControllerMVCIT {

  @Autowired private WebApplicationContext context;
  @Autowired private FeatureFlagManager featureFlags;
  @Autowired private UserManager userManager;
  @Autowired private InstrumentEntityApiManager instrumentManager;

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;
  private boolean originalBookingBaseline;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
    User sysadmin = sysadmin();
    originalBookingBaseline =
        featureFlags.getFeatureFlag(BOOKING_ENABLED, sysadmin).orElseThrow().isBaselineValue();
    setBookingEnabled(true);
  }

  @AfterEach
  void tearDown() {
    fixture.cleanUp();
    setBookingEnabled(originalBookingBaseline);
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v2/booking-catalogue")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v2/booking-catalogue/locations"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void appliesTheCompleteFilterBeforePaginationAndRejectsInvalidSelectors() throws Exception {
    long instrument = fixture.instrument(fixture.user(), fixture.marker());
    long configuration = fixture.bookingConfiguration(instrument, "UTC", fixture.userKey());
    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .header("apiKey", fixture.userKey())
                .queryParam("where", "id==" + configuration + ";id=ge=" + configuration))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .header("apiKey", fixture.userKey())
                .queryParam("where", "target==IN" + instrument + ";target==IN" + (instrument + 1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .header("apiKey", fixture.userKey())
                .queryParam("where", "password==secret"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void discoversConfiguredItemsWithoutEventsAndPagesInTheDatabase() throws Exception {
    long firstInstrument = fixture.instrument(fixture.user(), "Alpha scope " + fixture.marker());
    long secondInstrument = fixture.instrument(fixture.user(), "Beta scope " + fixture.marker());
    fixture.bookingConfiguration(firstInstrument, "UTC", fixture.userKey());
    fixture.bookingConfiguration(secondInstrument, "UTC", fixture.userKey());

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("q", fixture.marker())
                .queryParam("page", "1")
                .queryParam("limit", "1")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Alpha scope " + fixture.marker()));

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("q", fixture.marker())
                .queryParam("page", "2")
                .queryParam("limit", "1")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items[0].name").value("Beta scope " + fixture.marker()));
  }

  @Test
  void searchesByGlobalIdNameDescriptionAndReadableLocation() throws Exception {
    String nameMarker = "CatalogueNameMarker " + fixture.marker();
    String descriptionMarker = "CatalogueDescriptionMarker " + fixture.marker();
    String locationMarker = "CatalogueLocationMarker " + fixture.marker();
    long byName = fixture.instrument(fixture.user(), nameMarker);
    long byDescription =
        fixture.instrument(fixture.user(), "Unrelated instrument", descriptionMarker);
    ApiContainer parent = fixture.container(fixture.user(), locationMarker);
    long byLocation = fixture.instrumentIn(fixture.user(), "Another instrument", parent);
    fixture.bookingConfiguration(byName, "UTC", fixture.userKey());
    fixture.bookingConfiguration(byDescription, "UTC", fixture.userKey());
    fixture.bookingConfiguration(byLocation, "UTC", fixture.userKey());

    assertCatalogueSearch(nameMarker, "IN" + byName);
    assertCatalogueSearch(descriptionMarker, "IN" + byDescription);
    assertCatalogueSearch(locationMarker, "IN" + byLocation);
    assertCatalogueSearch("IN" + byDescription, "IN" + byDescription);
  }

  @Test
  void filtersByExactReadableImmediateParentAndPagesLocationOptions() throws Exception {
    ApiContainer parent = fixture.container(fixture.user(), "Imaging lab " + fixture.marker());
    String parentGlobalId = "IC" + parent.getId();
    long inParent = fixture.instrumentIn(fixture.user(), "Confocal " + fixture.marker(), parent);
    long outside = fixture.instrument(fixture.user(), "Centrifuge " + fixture.marker());
    fixture.bookingConfiguration(inParent, "UTC", fixture.userKey());
    fixture.bookingConfiguration(outside, "UTC", fixture.userKey());

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("location", parentGlobalId)
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].globalId").value("IN" + inParent))
        .andExpect(jsonPath("$.items[0].location.globalId").value(parentGlobalId));

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue/locations")
                .queryParam("q", "Imaging lab")
                .queryParam("limit", "1")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].globalId").value(parentGlobalId));

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("location", "IC999999999")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.items").isEmpty());

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("location", "BE" + parent.getId())
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void filtersAndCountsAvailabilityOnTheServer() throws Exception {
    long free = fixture.instrument(fixture.user(), "Free scope " + fixture.marker());
    long booked = fixture.instrument(fixture.user(), "Booked scope " + fixture.marker());
    fixture.bookingConfiguration(free, "UTC", fixture.userKey());
    fixture.bookingConfiguration(booked, "UTC", fixture.userKey());
    Instant tomorrow = Instant.parse("2099-01-02T10:00:00Z");
    fixture.booking(free, tomorrow, tomorrow.plusSeconds(3600));
    Instant ten = Instant.parse("2099-01-01T10:00:00Z");
    fixture.booking(booked, ten, ten.plusSeconds(3600));

    for (String[] expected :
        List.of(
            new String[] {"available-now", "IN" + free},
            new String[] {"free-later-today", "IN" + booked})) {
      mockMvc
          .perform(
              availabilityWindow(get("/api/v2/booking-catalogue"))
                  .queryParam("q", fixture.marker())
                  .queryParam("availability", expected[0])
                  .header("apiKey", fixture.userKey()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total").value(1))
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].globalId").value(expected[1]));
    }

    mockMvc
        .perform(
            availabilityWindow(get("/api/v2/booking-catalogue/availability-counts"))
                .queryParam("q", fixture.marker())
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availableNow").value(1))
        .andExpect(jsonPath("$.freeLaterToday").value(1));
    mockMvc
        .perform(
            availabilityWindow(get("/api/v2/booking-catalogue/availability-counts"))
                .queryParam("q", fixture.marker())
                .header("apiKey", fixture.otherUserKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availableNow").value(0))
        .andExpect(jsonPath("$.freeLaterToday").value(0));
  }

  @Test
  void rejectsAnUnknownAvailabilityOrAMissingWindow() throws Exception {
    mockMvc
        .perform(
            availabilityWindow(get("/api/v2/booking-catalogue"))
                .queryParam("availability", "free-soon")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("availability", "available-now")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/v2/booking-catalogue/availability-counts")
                .queryParam("availabilityStart", "2099-01-02T00:00:00Z")
                .queryParam("availabilityEnd", "2099-01-01T00:00:00Z")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isBadRequest());
  }

  /**
   * A principal investigator reads a member's owner-only item through role visibility alone. When
   * that item sits in a third person's Container the investigator cannot read, the item has no
   * readable location, while its owner reads the same Container through the item. Every location
   * operator must agree between its correlated SQL form (a top-level rule) and its listed form
   * (inside a disjunction), on the catalogue, the Calendar events and the Calendar resources.
   */
  @Test
  void locationFiltersFollowTheReadableParentRuleWithoutListingItems() throws Exception {
    User owner = fixture.user();
    User containerOwner = fixture.makeOwnerRoleVisibleTo(fixture.otherUser(), owner);
    fixture.makeOwnerRoleVisibleTo(fixture.thirdUser(), owner);
    owner = userManager.get(owner.getId());
    ApiContainer foreign = fixture.container(containerOwner, "Shared bench " + fixture.marker());
    ApiContainer own = fixture.container(owner, "Own bench " + fixture.marker());
    long hidden = fixture.instrumentIn(owner, "Hidden parent " + fixture.marker(), foreign);
    ApiInstrument ownerOnly = new ApiInstrument();
    ownerOnly.setId(hidden);
    ownerOnly.setSharingMode(ApiInventoryRecordInfo.ApiInventorySharingMode.OWNER_ONLY);
    instrumentManager.updateApiInstrument(ownerOnly, owner);
    long visible = fixture.instrumentIn(owner, "Visible parent " + fixture.marker(), own);
    fixture.bookingConfiguration(hidden, "UTC", fixture.userKey());
    fixture.bookingConfiguration(visible, "UTC", fixture.userKey());
    Instant morning = Instant.parse("2099-05-05T09:00:00Z");
    fixture.booking(hidden, morning, morning.plusSeconds(3600));
    fixture.booking(visible, morning.plusSeconds(7200), morning.plusSeconds(10800));
    String foreignId = "IC" + foreign.getId();
    String ownId = "IC" + own.getId();

    // Filter, then the matching items for the owner and for the investigator.
    Object[][] cases = {
      {"location=exists=true", List.of(hidden, visible), List.of(visible)},
      {"location=exists=false", List.of(), List.of(hidden)},
      {"location==" + foreignId, List.of(hidden), List.of()},
      {"location!=" + foreignId, List.of(visible), List.of(hidden, visible)},
      {"location=in=(" + foreignId + "," + ownId + ")", List.of(hidden, visible), List.of(visible)},
      {"location=out=(" + ownId + ")", List.of(hidden), List.of(hidden)},
    };
    for (Object[] expected : cases) {
      for (String where : List.of((String) expected[0], expected[0] + ",id==0")) {
        assertLocationFilter(where, fixture.userKey(), (List<?>) expected[1]);
        assertLocationFilter(where, fixture.thirdUserKey(), (List<?>) expected[2]);
      }
    }
    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("q", fixture.marker())
                .queryParam("where", "location=exists=false")
                .header("apiKey", fixture.thirdUserKey()))
        .andExpect(jsonPath("$.items[0].location").isEmpty());
  }

  private void assertLocationFilter(String where, String apiKey, List<?> items) throws Exception {
    String reason = where + " for " + (apiKey.equals(fixture.userKey()) ? "owner" : "investigator");
    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("q", fixture.marker())
                .queryParam("where", where)
                .header("apiKey", apiKey))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.total",
                org.hamcrest.Matchers.describedAs(reason, org.hamcrest.Matchers.is(items.size()))));
    mockMvc
        .perform(
            get("/api/v2/booking-calendar/events")
                .queryParam("start", "2099-05-05T00:00:00Z")
                .queryParam("end", "2099-05-06T00:00:00Z")
                .queryParam("q", fixture.marker())
                .queryParam("where", where)
                .header("apiKey", apiKey))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.totalDocs",
                org.hamcrest.Matchers.describedAs(
                    "events " + reason, org.hamcrest.Matchers.is(items.size()))));
    mockMvc
        .perform(
            get("/api/v2/booking-catalogue/calendar")
                .queryParam("calendarStart", "2099-05-05T00:00:00Z")
                .queryParam("calendarEnd", "2099-05-06T00:00:00Z")
                .queryParam("q", fixture.marker())
                .queryParam("eventWhere", where)
                .header("apiKey", apiKey))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.total",
                org.hamcrest.Matchers.describedAs(
                    "resources " + reason, org.hamcrest.Matchers.is(items.size()))));
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      availabilityWindow(
          org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
    return request
        .queryParam("availabilityStart", "2099-01-01T00:00:00Z")
        .queryParam("availabilityEnd", "2099-01-02T00:00:00Z")
        .queryParam("now", "2099-01-01T10:30:00Z");
  }

  @Test
  void unsupportedTypeDoesNotAdvertiseInstrumentFacet() throws Exception {
    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("type", "NOTATYPE")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.facets.types").isEmpty());
  }

  @Test
  void noMatchDoesNotAdvertiseInstrumentFacet() throws Exception {
    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("q", "no catalogue item has this marker " + fixture.marker())
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.facets.types").isEmpty());
  }

  @Test
  void catalogueIsHiddenWhenBookingIsDisabled() throws Exception {
    setBookingEnabled(false);

    mockMvc
        .perform(get("/api/v2/booking-catalogue").header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v2/booking-catalogue/locations").header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());

    setBookingEnabled(true);
  }

  @Test
  void calendarEndpointsHideBeforeParsingDisabledFeatureQueries() throws Exception {
    setBookingEnabled(false);

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue/calendar")
                .queryParam("calendarStart", "2026-09-20T00:00:00Z")
                .queryParam("calendarEnd", "2026-09-21T00:00:00Z")
                .queryParam("where", "password==secret")
                .queryParam("eventWhere", "password==secret")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/v2/booking-calendar/events")
                .queryParam("start", "2026-09-20T00:00:00Z")
                .queryParam("end", "2026-09-21T00:00:00Z")
                .queryParam("where", "password==secret")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());
  }

  @Test
  void calendarEndpointsRejectInvalidIntervalsWhenEnabled() throws Exception {
    mockMvc
        .perform(
            get("/api/v2/booking-catalogue/calendar")
                .queryParam("calendarStart", "2026-09-21T00:00:00Z")
                .queryParam("calendarEnd", "2026-09-20T00:00:00Z")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/v2/booking-calendar/events")
                .queryParam("start", "2026-09-20T00:00:00Z")
                .queryParam("end", "2026-09-20T00:00:00Z")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void calendarEventsHonorTheRequestedSparseFieldset() throws Exception {
    long instrument = fixture.instrument(fixture.user(), "Calendar projection " + fixture.marker());
    fixture.bookingConfiguration(instrument, "UTC", fixture.userKey());
    Instant start = Instant.parse("2099-01-01T10:00:00Z");
    fixture.booking(instrument, start, start.plusSeconds(3600));

    mockMvc
        .perform(
            get("/api/v2/booking-calendar/events")
                .queryParam("start", "2099-01-01T00:00:00Z")
                .queryParam("end", "2099-01-02T00:00:00Z")
                .queryParam("fields[bookings]", "id,start")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs[0].id").exists())
        .andExpect(jsonPath("$.docs[0].start").exists())
        .andExpect(jsonPath("$.docs[0].end").doesNotExist())
        .andExpect(jsonPath("$.docs[0].purpose").doesNotExist())
        .andExpect(jsonPath("$.docs[0].target").doesNotExist());
  }

  @Test
  void calendarEventsSupportOnlyTheCalendarLocalDerivedFacets() throws Exception {
    long instrument = fixture.instrument(fixture.user(), "Calendar filters " + fixture.marker());
    String timezone = ZoneId.systemDefault().getId();
    fixture.bookingConfiguration(instrument, timezone, fixture.userKey());
    Instant start = Instant.parse("2099-01-01T10:00:00Z");
    fixture.booking(instrument, start, start.plusSeconds(3600));

    mockMvc
        .perform(
            get("/api/v2/booking-calendar/events")
                .queryParam("start", "2099-01-01T00:00:00Z")
                .queryParam("end", "2099-01-02T00:00:00Z")
                .queryParam(
                    "where",
                    "privacy==full;timezone==\""
                        + timezone
                        + "\";bookedBy=contains=\""
                        + fixture.user().getFullName()
                        + "\";purpose=exists=false;requesterId=="
                        + fixture.user().getId())
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(1));

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue/calendar")
                .queryParam("calendarStart", "2099-01-01T00:00:00Z")
                .queryParam("calendarEnd", "2099-01-02T00:00:00Z")
                .queryParam(
                    "eventWhere",
                    "privacy==full;timezone==\""
                        + timezone
                        + "\";bookedBy=contains="
                        + fixture.user().getUsername()
                        + ";purpose=exists=false;requesterId=="
                        + fixture.user().getId())
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));

    mockMvc
        .perform(
            get("/api/v2/bookings")
                .queryParam("where", "purpose=exists=false")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDocs").value(1));
  }

  @Test
  void calendarTimezoneFiltersIgnoreRetainedBookingsAfterItemAccessIsLost() throws Exception {
    User formerOwner = fixture.user();
    User newOwner = fixture.otherUser();
    long instrument =
        fixture.instrument(formerOwner, "Calendar hidden timezone " + fixture.marker());
    String timezone = ZoneId.systemDefault().getId();
    fixture.bookingConfiguration(instrument, timezone, fixture.userKey());
    Instant start = Instant.parse("2099-01-01T10:00:00Z");
    fixture.booking(instrument, start, start.plusSeconds(3600));

    ApiInstrument transferred = instrumentManager.getApiInstrumentById(instrument, formerOwner);
    transferred.setOwner(new ApiUser(newOwner));
    instrumentManager.changeApiInstrumentOwner(transferred, formerOwner);

    for (String where :
        List.of(
            "timezone==\"" + timezone + "\"",
            "timezone!=\"" + timezone + "\"",
            "timezone=in=(\"" + timezone + "\")",
            "timezone=out=(\"__unmatched_timezone__\")",
            "timezone=exists=true",
            "timezone=exists=false")) {
      mockMvc
          .perform(
              get("/api/v2/booking-calendar/events")
                  .queryParam("start", "2099-01-01T00:00:00Z")
                  .queryParam("end", "2099-01-02T00:00:00Z")
                  .queryParam("where", where)
                  .header("apiKey", fixture.userKey()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalDocs").value(0));
    }
  }

  private User sysadmin() {
    return userManager.getUserByUsername(AbstractAppInitializor.SYSADMIN_UNAME);
  }

  private void assertCatalogueSearch(String query, String expectedGlobalId) throws Exception {
    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("q", query)
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].globalId").value(expectedGlobalId));
  }

  private void setBookingEnabled(boolean enabled) {
    User sysadmin = sysadmin();
    featureFlags
        .updateFeatureFlag(
            BOOKING_ENABLED, new FeatureFlagManager.Patch(enabled, false, null), sysadmin, sysadmin)
        .orElseThrow();
  }
}
