package com.researchspace.api.v2.controller;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
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
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final WebApplicationContext context;
  private final FeatureFlagManager featureFlags;
  private final UserManager userManager;
  private final InstrumentEntityApiManager instrumentManager;

  @Autowired
  BookingCatalogueControllerMVCIT(
      WebApplicationContext context,
      FeatureFlagManager featureFlags,
      UserManager userManager,
      InstrumentEntityApiManager instrumentManager) {
    this.context = context;
    this.featureFlags = featureFlags;
    this.userManager = userManager;
    this.instrumentManager = instrumentManager;
  }

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
  void calendarEventsRenderOnlyProjectedRuntimeValuesAndRedactThemWithTheTarget() throws Exception {
    User owner = fixture.user();
    String filterValue = "filter " + fixture.marker();
    String projectedValue = "projected " + fixture.marker();
    ApiInventoryEntityField filterField = textField("filter field", filterValue);
    ApiInventoryEntityField projectedField = textField("projected field", projectedValue);
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("Calendar runtime " + fixture.marker());
    templatePost.setFields(List.of(filterField, projectedField));
    var template = instrumentManager.createInstrumentTemplate(templatePost, owner);
    ApiInstrument requested = new ApiInstrument();
    requested.setName("Calendar runtime instrument " + fixture.marker());
    requested.setTemplateId(template.getId());
    long instrument = instrumentManager.createNewApiInstrument(requested, owner).getId();
    ApiInstrument readableTemplatePeer = new ApiInstrument();
    readableTemplatePeer.setName("Calendar runtime peer " + fixture.marker());
    readableTemplatePeer.setTemplateId(template.getId());
    instrumentManager.createNewApiInstrument(readableTemplatePeer, owner);
    fixture.bookingConfiguration(instrument, "UTC", fixture.userKey());
    Instant start = Instant.parse("2099-01-01T10:00:00Z");
    long bookingId = fixture.booking(instrument, start, start.plusSeconds(3600));
    String filterId = "SF" + template.getFields().get(0).getId();
    String projectedId = "SF" + template.getFields().get(1).getId();
    String filterSelector = "target.customFields." + filterId;
    String projectedSelector = "target.customFields." + projectedId;

    JsonNode projectionOnly = calendarEvents(null, "id," + projectedSelector);
    assertEquals(1, projectionOnly.path("totalDocs").asInt());
    assertEquals(1, projectionOnly.path("docs").size());
    assertEquals(bookingId, projectionOnly.path("docs").get(0).path("id").asLong());
    assertEquals(
        projectedValue,
        projectionOnly.path("docs").get(0).path("target.customFields").path(projectedId).asText());
    assertFalse(projectionOnly.path("docs").get(0).path("target.customFields").has(filterId));

    JsonNode independentlyFiltered =
        calendarEvents(filterSelector + "==\"" + filterValue + "\"", "id," + projectedSelector);
    assertEquals(1, independentlyFiltered.path("totalDocs").asInt());
    assertEquals(
        projectedValue,
        independentlyFiltered
            .path("docs")
            .get(0)
            .path("target.customFields")
            .path(projectedId)
            .asText());
    assertFalse(
        independentlyFiltered.path("docs").get(0).path("target.customFields").has(filterId));

    JsonNode filterOnly = calendarEvents(filterSelector + "==\"" + filterValue + "\"", "id");
    assertFalse(filterOnly.path("docs").get(0).has("target.customFields"));

    ApiInstrument transferred = instrumentManager.getApiInstrumentById(instrument, owner);
    transferred.setOwner(new ApiUser(fixture.otherUser()));
    instrumentManager.changeApiInstrumentOwner(transferred, owner);

    JsonNode redactedTarget = calendarEvents(null, "id,target");
    assertEquals(1, redactedTarget.path("totalDocs").asInt());
    assertEquals(bookingId, redactedTarget.path("docs").get(0).path("id").asLong());
    assertTrue(redactedTarget.path("docs").get(0).path("target").isNull());

    JsonNode redactedProjection = calendarEvents(null, "id," + projectedSelector);
    assertEquals(1, redactedProjection.path("totalDocs").asInt());
    assertEquals(bookingId, redactedProjection.path("docs").get(0).path("id").asLong());
    assertFalse(
        redactedProjection.path("docs").get(0).path("target.customFields").has(projectedId));
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

  private JsonNode calendarEvents(String where, String fields) throws Exception {
    var request =
        get("/api/v2/booking-calendar/events")
            .queryParam("start", "2099-01-01T00:00:00Z")
            .queryParam("end", "2099-01-02T00:00:00Z")
            .queryParam("page", "1")
            .queryParam("limit", "1")
            .queryParam("fields[bookings]", fields)
            .header("apiKey", fixture.userKey());
    if (where != null) request.queryParam("where", where);
    String body =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return OBJECT_MAPPER.readTree(body);
  }

  private static ApiInventoryEntityField textField(String name, String content) {
    ApiInventoryEntityField field = new ApiInventoryEntityField();
    field.setName(name);
    field.setType(ApiFieldType.TEXT);
    field.setContent(content);
    return field;
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
