package com.researchspace.api.v2.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.model.inventory.field.ExtraFieldIdentity;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

@ApiV2WebIntegrationTest
class InstrumentParentLocationQueryCountMVCIT {

  private static final Instant BOOKING_START = Instant.parse("2035-01-15T09:00:00Z");
  private static final Instant BOOKING_END = Instant.parse("2035-01-15T10:00:00Z");
  private static final String INSTRUMENTS = "/api/v2/instruments";
  private static final String LOCATION_FIELDS =
      "id,name,parentContainerName,parentContainerGlobalId";
  private static final String CUSTOM_VALUE = "BSL-2";
  private static final String EXTRA_VALUE = "Imaging lab";
  private static final String INVENTORY_PACKAGE = "com.researchspace.model.inventory.";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private SessionFactory sessionFactory;
  @Autowired private ContainerDao containerDao;
  @Autowired private InstrumentDao instrumentDao;
  @Autowired private InstrumentEntityApiManager instrumentManager;
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
    fixture.userKey();
  }

  @AfterEach
  void tearDown() {
    fixture.cleanUp();
    statistics.clear();
    statistics.setStatisticsEnabled(false);
  }

  @Test
  void masksParentLocationsAndOmitsThemFromBookingTargets() throws Exception {
    FixtureRows rows = createRows(5, "Mixed" + fixture.marker());
    assertLocations(request(INSTRUMENTS, rows.instrumentIds(), LOCATION_FIELDS).body(), rows);
    assertParentLocation(
        singleInstrument(rows.instrumentIds().get(1), null),
        rows.sharedParentName(),
        "IC" + rows.sharedParentId());
    assertParentLocation(singleInstrument(rows.instrumentIds().get(3), null), null, null);
    assertParentLocation(singleInstrument(rows.instrumentIds().get(4), null), null, null);

    // Booking's safe target projection must not regain private inventory location fields.
    for (String path : List.of("/api/v2/booking-configurations", "/api/v2/bookings")) {
      JsonNode documents = request(path, rows.instrumentIds(), "id,target").body().path("docs");
      for (JsonNode document : documents) {
        JsonNode target = document.path("target").path("value");
        assertTrue(target.path("name").isTextual());
        assertTrue(target.path("parentContainerName").isMissingNode());
        assertTrue(target.path("parentContainerGlobalId").isMissingNode());
      }
    }

    String where = idFilter(rows.instrumentIds());
    instrumentCount(where, null, 5);
    long withoutLocations = instrumentCount(where, null, 5);
    assertEquals(withoutLocations, instrumentCount(where, LOCATION_FIELDS, 5));

    for (String query : List.of("where", "sort")) {
      var request =
          get(INSTRUMENTS)
              .header("apiKey", fixture.userKey())
              .param(
                  query,
                  query.equals("where")
                      ? "parentContainerName==" + rows.sharedParentName()
                      : "parentContainerName");
      mockMvc.perform(request).andExpect(status().isBadRequest());
    }
  }

  @Test
  void scalarRequestsPreserveValuesPagesAndFiltersWithConstantQueries() throws Exception {
    ScalarRows rows = createScalarRows();
    List<Long> oneId = List.of(rows.instruments().get(0).id());
    List<Long> allIds = rows.instrumentIds();
    String customSelector = "customFields." + rows.customId();
    String extraSelector = "extraFields." + rows.extraId();
    request(INSTRUMENTS, oneId, "id,name");

    for (String fields :
        List.of(
            "id,name,globalId,deleted",
            LOCATION_FIELDS,
            "id,name," + customSelector,
            "id,name," + extraSelector)) {
      MeasuredResponse one = request(INSTRUMENTS, oneId, fields);
      MeasuredResponse many = request(INSTRUMENTS, allIds, fields);
      assertBounded(one.statements(), many.statements(), "complete request for " + fields);
      assertEquals(25, many.body().path("totalDocs").asInt());
      for (int index = 0; index < rows.instruments().size(); index++) {
        InstrumentRow expected = rows.instruments().get(index);
        JsonNode document = many.body().path("docs").get(index);
        assertEquals(expected.id(), document.path("id").asLong());
        assertEquals(expected.name(), document.path("name").asText());
        if (fields.contains("globalId")) {
          assertEquals("IN" + expected.id(), document.path("globalId").asText());
          assertFalse(document.get("deleted").asBoolean());
        }
        if (fields.equals(LOCATION_FIELDS)) {
          assertParentLocation(document, expected.parentName(), "IC" + expected.parentId());
        }
        if (fields.contains(customSelector)) {
          assertEquals(CUSTOM_VALUE, document.path("customFields").path(rows.customId()).asText());
        }
        if (fields.contains(extraSelector)) {
          assertEquals(EXTRA_VALUE, document.path("extraFields").path(rows.extraId()).asText());
        }
      }
    }

    List<InstrumentRow> descending =
        rows.instruments().stream()
            .sorted(Comparator.comparing(InstrumentRow::name).reversed())
            .toList();
    for (int page : List.of(2, 5, 6)) {
      MeasuredResponse response =
          measuredRequest(
              get(INSTRUMENTS)
                  .header("apiKey", fixture.userKey())
                  .param("where", idFilter(allIds))
                  .param("sort", "-name,id")
                  .param("limit", "5")
                  .param("page", Integer.toString(page))
                  .param("fields[instruments]", "id,name," + customSelector),
              true);
      int from = Math.min((page - 1) * 5, descending.size());
      List<InstrumentRow> expected =
          descending.subList(from, Math.min(from + 5, descending.size()));
      assertEquals(expected.stream().map(InstrumentRow::id).toList(), documentIds(response.body()));
      assertEquals(25, response.body().path("totalDocs").asInt());
    }

    for (String value : List.of(CUSTOM_VALUE, "BSL-3")) {
      String where = idFilter(allIds) + ";" + customSelector + "==" + value;
      int expected = value.equals(CUSTOM_VALUE) ? 25 : 0;
      JsonNode filtered =
          measuredRequest(
                  get(INSTRUMENTS)
                      .header("apiKey", fixture.userKey())
                      .param("where", where)
                      .param("limit", "100")
                      .param("fields[instruments]", "id"),
                  true)
              .body();
      assertEquals(expected == 0 ? List.of() : allIds, documentIds(filtered));
      assertEquals(expected, filtered.path("totalDocs").asInt());
      instrumentCount(where, null, expected);
    }

    InstrumentRow first = rows.instruments().get(0);
    JsonNode single =
        singleInstrument(first.id(), LOCATION_FIELDS + "," + customSelector + "," + extraSelector);
    assertEquals(first.id(), single.path("id").asLong());
    assertEquals(first.name(), single.path("name").asText());
    assertParentLocation(single, first.parentName(), "IC" + first.parentId());
    assertEquals(CUSTOM_VALUE, single.path("customFields").path(rows.customId()).asText());
    assertEquals(EXTRA_VALUE, single.path("extraFields").path(rows.extraId()).asText());

    JsonNode sparse = singleInstrument(first.id(), customSelector);
    assertEquals(first.id(), sparse.path("id").asLong());
    assertEquals(CUSTOM_VALUE, sparse.path("customFields").path(rows.customId()).asText());
    assertEquals(2, sparse.size());
  }

  private ScalarRows createScalarRows() {
    String label = "Scalar " + fixture.marker().substring(0, 18);
    String extraName = label + " room";
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName(label + " template");
    ApiInventoryEntityField custom = new ApiInventoryEntityField();
    custom.setName(label + " custom");
    custom.setType(ApiFieldType.TEXT);
    custom.setContent(CUSTOM_VALUE);
    templatePost.setFields(List.of(custom));
    var template = instrumentManager.createInstrumentTemplate(templatePost, owner);
    List<InstrumentRow> instruments = new ArrayList<>();
    for (int index = 0; index < 25; index++) {
      ApiContainer parent = fixture.container(owner, label + " parent " + index);
      ApiInstrument requested = new ApiInstrument();
      requested.setName(label + " instrument " + index);
      requested.setTemplateId(template.getId());
      ApiExtraField extra = new ApiExtraField();
      extra.setName(extraName);
      extra.setType(ExtraFieldTypeEnum.TEXT);
      extra.setContent(EXTRA_VALUE);
      requested.setExtraFields(List.of(extra));
      ApiInstrument created = instrumentManager.createNewApiInstrument(requested, owner);
      ApiInstrument move = new ApiInstrument();
      move.setId(created.getId());
      move.setParentContainer(parent);
      instrumentManager.updateApiInstrument(move, owner);
      instruments.add(
          new InstrumentRow(created.getId(), created.getName(), parent.getId(), parent.getName()));
    }
    return new ScalarRows(
        instruments.stream().sorted(Comparator.comparing(InstrumentRow::name)).toList(),
        "SF" + template.getFields().get(0).getId(),
        ExtraFieldIdentity.encode(extraName, FieldType.TEXT));
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
    return new FixtureRows(List.copyOf(ids), shared.getId(), label + " shared parent");
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

  private MeasuredResponse request(String path, List<Long> ids, String fields) throws Exception {
    MeasuredResponse response =
        measuredRequest(
            get(path)
                .header("apiKey", fixture.userKey())
                .param("where", path.equals(INSTRUMENTS) ? idFilter(ids) : targetFilter(ids))
                .param("depth", "1")
                .param("limit", "100")
                .param("fields[" + path.substring("/api/v2/".length()) + "]", fields),
            path.equals(INSTRUMENTS));
    assertEquals(ids.size(), response.body().path("docs").size());
    return response;
  }

  private JsonNode singleInstrument(long id, String fields) throws Exception {
    var request = get(INSTRUMENTS + "/" + id).header("apiKey", fixture.userKey());
    if (fields != null) {
      request.param("fields[instruments]", fields);
    }
    return measuredRequest(request, true).body();
  }

  private MeasuredResponse measuredRequest(
      MockHttpServletRequestBuilder request, boolean scalarInstrumentRead) throws Exception {
    clearSession();
    statistics.clear();
    MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    if (scalarInstrumentRead) {
      assertNoInventoryHydration();
    }
    return new MeasuredResponse(
        statistics.getPrepareStatementCount(),
        OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()));
  }

  private long instrumentCount(String where, String fields, int expected) throws Exception {
    var request =
        get(INSTRUMENTS + "/count").header("apiKey", fixture.userKey()).param("where", where);
    if (fields != null) {
      request.param("fields[instruments]", fields);
    }
    MeasuredResponse response = measuredRequest(request, false);
    assertEquals(expected, response.body().path("totalDocs").asInt());
    return response.statements();
  }

  private void clearSession() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            ignored -> {
              sessionFactory.getCurrentSession().flush();
              sessionFactory.getCurrentSession().clear();
            });
    // Warm-up requests must not hide entity hydration through the second-level inventory cache.
    for (String entity : statistics.getEntityNames()) {
      if (entity.startsWith(INVENTORY_PACKAGE)) {
        sessionFactory.getCache().evictEntityData(entity);
      }
    }
  }

  private void assertNoInventoryHydration() {
    for (String entity : statistics.getEntityNames()) {
      if (entity.startsWith(INVENTORY_PACKAGE)) {
        var entityStatistics = statistics.getEntityStatistics(entity);
        assertEquals(0L, entityStatistics.getLoadCount(), entity + " loads");
        assertEquals(0L, entityStatistics.getFetchCount(), entity + " fetches");
      }
    }
  }

  private static void assertBounded(long one, long twentyFive, String endpoint) {
    assertTrue(
        twentyFive <= one + 1,
        () ->
            endpoint
                + " prepared "
                + twentyFive
                + " statements for 25 rows after "
                + one
                + " for one row");
  }

  private static void assertLocations(JsonNode body, FixtureRows rows) {
    JsonNode shared = targetNamed(body, "instrument 1");
    assertParentLocation(shared, rows.sharedParentName(), "IC" + rows.sharedParentId());

    JsonNode workbench = targetNamed(body, "instrument 0");
    assertTrue(workbench.get("parentContainerGlobalId").asText().startsWith("BE"));

    assertParentLocation(targetNamed(body, "instrument 3"), null, null);
    assertParentLocation(targetNamed(body, "instrument 4"), null, null);
  }

  private static void assertParentLocation(JsonNode document, String name, String globalId) {
    if (name == null) {
      assertTrue(document.path("parentContainerName").isNull());
      assertTrue(document.path("parentContainerGlobalId").isNull());
    } else {
      assertEquals(name, document.path("parentContainerName").asText());
      assertEquals(globalId, document.path("parentContainerGlobalId").asText());
    }
  }

  private static JsonNode targetNamed(JsonNode body, String suffix) {
    for (JsonNode document : body.get("docs")) {
      JsonNode target = document;
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

  private static List<Long> documentIds(JsonNode body) {
    List<Long> ids = new ArrayList<>();
    body.path("docs").forEach(document -> ids.add(document.path("id").asLong()));
    return ids;
  }

  private static String idFilter(List<Long> ids) {
    return "id=in=("
        + ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))
        + ")";
  }

  private record FixtureRows(
      List<Long> instrumentIds, long sharedParentId, String sharedParentName) {}

  private record InstrumentRow(long id, String name, long parentId, String parentName) {}

  private record ScalarRows(List<InstrumentRow> instruments, String customId, String extraId) {
    List<Long> instrumentIds() {
      return instruments.stream().map(InstrumentRow::id).toList();
    }
  }

  private record MeasuredResponse(long statements, JsonNode body) {}
}
