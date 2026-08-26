package com.researchspace.api.v2.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

@ApiV2WebIntegrationTest
class InstrumentParentLocationQueryCountMVCIT {

  private static final Instant BOOKING_START = Instant.parse("2035-01-15T09:00:00Z");
  private static final Instant BOOKING_END = Instant.parse("2035-01-15T10:00:00Z");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private SessionFactory sessionFactory;
  @Autowired private ContainerDao containerDao;
  @Autowired private InstrumentDao instrumentDao;
  @Autowired private PlatformTransactionManager transactionManager;

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;
  private Statistics statistics;
  private User owner;
  private User roleVisibleOwner;
  private User unreadableParentOwner;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
    statistics = sessionFactory.getStatistics();
    statistics.setStatisticsEnabled(true);
    roleVisibleOwner = fixture.otherUser();
    unreadableParentOwner = fixture.thirdUser();
    owner = fixture.makeOwnerRoleVisibleTo(fixture.user(), roleVisibleOwner);
  }

  @AfterEach
  void tearDown() {
    fixture.cleanUp();
    statistics.clear();
    statistics.setStatisticsEnabled(false);
  }

  @Test
  void bookingAndConfigurationLocationQueriesStayConstantForTwentyFiveRows() throws Exception {
    FixtureRows one = createRows(1, "One" + fixture.marker());
    FixtureRows twentyFive = createRows(25, "Many" + fixture.marker());
    clearSession();

    MeasuredResponse oneConfiguration =
        request("/api/v2/booking-configurations", one, "id,target,enabled,timezone");
    MeasuredResponse manyConfigurations =
        request("/api/v2/booking-configurations", twentyFive, "id,target,enabled,timezone");
    assertBounded(oneConfiguration, manyConfigurations, "booking configurations");
    assertLocations(manyConfigurations.body(), twentyFive);

    MeasuredResponse oneBooking =
        request("/api/v2/bookings", one, "id,target,timezone,start,end,state");
    MeasuredResponse manyBookings =
        request("/api/v2/bookings", twentyFive, "id,target,timezone,start,end,state");
    assertBounded(oneBooking, manyBookings, "bookings");
    assertLocations(manyBookings.body(), twentyFive);
  }

  @Test
  void instrumentCountDoesNotFetchParentLocations() throws Exception {
    FixtureRows rows = createRows(1, "Count" + fixture.marker());
    clearSession();

    long withoutLocations = instrumentCount(rows, null);
    long withLocations = instrumentCount(rows, "id,parentContainerName,parentContainerGlobalId");

    assertEquals(withoutLocations, withLocations);
  }

  @Test
  void directInstrumentReadsMaskDeletedParentsAndRejectLocationQueries() throws Exception {
    FixtureRows rows = createRows(5, "Direct" + fixture.marker());
    clearSession();

    mockMvc
        .perform(
            get("/api/v2/instruments/" + rows.instrumentIds().get(1))
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parentContainerName").value(rows.sharedParentName()))
        .andExpect(jsonPath("$.parentContainerGlobalId").value("IC" + rows.sharedParentId()));
    mockMvc
        .perform(
            get("/api/v2/instruments/" + rows.instrumentIds().get(3))
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parentContainerName").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.parentContainerGlobalId").value(org.hamcrest.Matchers.nullValue()));
    mockMvc
        .perform(
            get("/api/v2/instruments/" + rows.instrumentIds().get(4))
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parentContainerName").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.parentContainerGlobalId").value(org.hamcrest.Matchers.nullValue()));

    for (String query : List.of("where", "sort")) {
      var request =
          get("/api/v2/instruments")
              .header("apiKey", fixture.userKey())
              .param(
                  query,
                  query.equals("where")
                      ? "parentContainerName==" + rows.sharedParentName()
                      : "parentContainerName");
      mockMvc.perform(request).andExpect(status().isBadRequest());
    }
  }

  private FixtureRows createRows(int count, String label) {
    ApiContainer shared = fixture.container(owner, label + " shared parent");
    ApiContainer deleted = fixture.container(owner, label + " deleted parent");
    ApiContainer unreadable =
        fixture.container(unreadableParentOwner, label + " unreadable parent");
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            ignored -> {
              Container parent = containerDao.get(unreadable.getId());
              parent.setSharingMode(InventorySharingMode.OWNER_ONLY);
              containerDao.save(parent);
            });
    List<Long> ids = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      String name = label + " instrument " + index;
      long instrumentId =
          switch (index % 5) {
            case 0 -> fixture.instrument(owner, name);
            case 1 -> fixture.instrumentIn(owner, name, shared);
            case 2 ->
                fixture.instrumentIn(
                    owner, name, fixture.container(owner, label + " distinct parent " + index));
            case 3 -> fixture.instrumentIn(owner, name, deleted);
            default -> instrumentInUnreadableParent(roleVisibleOwner, name, unreadable);
          };
      ids.add(instrumentId);
      fixture.bookingConfiguration(instrumentId, "Europe/Berlin");
      fixture.booking(instrumentId, BOOKING_START, BOOKING_END);
    }
    if (count > 3) {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              ignored -> {
                Container parent = containerDao.get(deleted.getId());
                parent.setRecordDeleted(true);
                containerDao.save(parent);
              });
    }
    return new FixtureRows(
        List.copyOf(ids),
        shared.getId(),
        label + " shared parent",
        deleted.getId(),
        label + " deleted parent");
  }

  private long instrumentInUnreadableParent(User owner, String name, ApiContainer parent) {
    long instrumentId = fixture.instrument(owner, name);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            ignored -> {
              Instrument instrument = instrumentDao.get(instrumentId);
              instrument.setSharingMode(InventorySharingMode.OWNER_ONLY);
              instrument.moveToNewParent(containerDao.get(parent.getId()));
              instrumentDao.save(instrument);
            });
    return instrumentId;
  }

  private MeasuredResponse request(String path, FixtureRows rows, String fields) throws Exception {
    clearSession();
    statistics.clear();
    MvcResult result =
        mockMvc
            .perform(
                get(path)
                    .header("apiKey", fixture.userKey())
                    .param("where", targetFilter(rows.instrumentIds()))
                    .param("depth", "1")
                    .param("limit", "100")
                    .param("fields[" + path.substring("/api/v2/".length()) + "]", fields))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.docs.length()").value(rows.instrumentIds().size()))
            .andReturn();
    return new MeasuredResponse(
        statistics.getPrepareStatementCount(),
        OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()));
  }

  private long instrumentCount(FixtureRows rows, String fields) throws Exception {
    clearSession();
    statistics.clear();
    var request =
        get("/api/v2/instruments/count")
            .header("apiKey", fixture.userKey())
            .param("where", idFilter(rows.instrumentIds()));
    if (fields != null) {
      request.param("fields[instruments]", fields);
    }
    mockMvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.totalDocs").value(1));
    return statistics.getPrepareStatementCount();
  }

  private void clearSession() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            ignored -> {
              sessionFactory.getCurrentSession().flush();
              sessionFactory.getCurrentSession().clear();
            });
  }

  private static void assertBounded(
      MeasuredResponse one, MeasuredResponse twentyFive, String endpoint) {
    assertTrue(
        twentyFive.statements() <= one.statements() + 1,
        () ->
            endpoint
                + " prepared "
                + twentyFive.statements()
                + " statements for 25 rows after "
                + one.statements()
                + " for one row");
  }

  private static void assertLocations(JsonNode body, FixtureRows rows) {
    JsonNode shared = targetNamed(body, "instrument 1");
    assertEquals(rows.sharedParentName(), shared.get("parentContainerName").asText());
    assertEquals("IC" + rows.sharedParentId(), shared.get("parentContainerGlobalId").asText());

    JsonNode workbench = targetNamed(body, "instrument 0");
    assertTrue(workbench.get("parentContainerGlobalId").asText().startsWith("BE"));

    JsonNode deleted = targetNamed(body, "instrument 3");
    assertTrue(deleted.get("parentContainerName").isNull());
    assertTrue(deleted.get("parentContainerGlobalId").isNull());

    JsonNode unreadable = targetNamed(body, "instrument 4");
    assertTrue(unreadable.get("parentContainerName").isNull());
    assertTrue(unreadable.get("parentContainerGlobalId").isNull());
  }

  private static JsonNode targetNamed(JsonNode body, String suffix) {
    for (JsonNode document : body.get("docs")) {
      JsonNode target = document.path("target").path("value");
      if (target.path("name").asText().endsWith(suffix)) {
        return target;
      }
    }
    throw new AssertionError("No expanded instrument ending with " + suffix);
  }

  private static String targetFilter(List<Long> ids) {
    return "target=in=("
        + ids.stream().map(id -> "IN" + id).collect(java.util.stream.Collectors.joining(","))
        + ")";
  }

  private static String idFilter(List<Long> ids) {
    return "id=in=("
        + ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))
        + ")";
  }

  private record FixtureRows(
      List<Long> instrumentIds,
      long sharedParentId,
      String sharedParentName,
      long deletedParentId,
      String deletedParentName) {}

  private record MeasuredResponse(long statements, JsonNode body) {}
}
