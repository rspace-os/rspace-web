package com.researchspace.api.v2.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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
  private static final String LOCATION_FIELDS = "id,name,parentContainer";
  private static final String CUSTOM_VALUE = "BSL-2";
  private static final String EXTRA_VALUE = "Imaging lab";
  private static final String INVENTORY_PACKAGE = "com.researchspace.model.inventory.";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final WebApplicationContext context;
  private final SessionFactory sessionFactory;
  private final ContainerDao containerDao;
  private final InstrumentDao instrumentDao;
  private final InstrumentEntityApiManager instrumentManager;
  private final PlatformTransactionManager transactionManager;

  @Autowired
  InstrumentParentLocationQueryCountMVCIT(
      WebApplicationContext context,
      SessionFactory sessionFactory,
      ContainerDao containerDao,
      InstrumentDao instrumentDao,
      InstrumentEntityApiManager instrumentManager,
      PlatformTransactionManager transactionManager) {
    this.context = context;
    this.sessionFactory = sessionFactory;
    this.containerDao = containerDao;
    this.instrumentDao = instrumentDao;
    this.instrumentManager = instrumentManager;
    this.transactionManager = transactionManager;
  }

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
  void bookingLocationExpansionRunsNoContainerCountStatement() throws Exception {
    FixtureRows rows = createRows(5, "LocationCount" + fixture.marker());
    var logger =
        (org.apache.logging.log4j.core.Logger)
            org.apache.logging.log4j.LogManager.getLogger("org.hibernate.SQL");
    var level = logger.getLevel();
    var statements = new com.researchspace.core.testutil.StringAppenderForTestLogging();
    statements.start();
    logger.addAppender(statements);
    logger.setLevel(org.apache.logging.log4j.Level.DEBUG);
    try {
      JsonNode docs =
          request("/api/v2/bookings", rows.instrumentIds(), "id,location").body().path("docs");
      assertFalse(docs.isEmpty());
      String sql = statements.logContents.toLowerCase(java.util.Locale.ROOT);
      assertTrue(sql.contains("select"), "SQL capture must contain executed statements");
      assertFalse(
          java.util.regex.Pattern.compile("select count\\((?:(?!select).)*?from container ")
              .matcher(sql)
              .find(),
          sql);
    } finally {
      logger.removeAppender(statements);
      logger.setLevel(level);
      statements.stop();
    }
  }

  @Test
  void bookingLocationsUseContainerReferencesAndHideUnreadableParentsAtEveryDepth()
      throws Exception {
    FixtureRows rows = createRows(5, "BookingLocation" + fixture.marker());
    for (int depth : List.of(0, 1)) {
      JsonNode docs =
          measuredRequest(
                  get("/api/v2/bookings")
                      .header("apiKey", fixture.userKey())
                      .param("where", targetFilter(rows.instrumentIds()))
                      .param("depth", Integer.toString(depth))
                      .param("fields[bookings]", "id,target,location")
                      .param("fields[instruments]", "id")
                      .param("fields[containers]", "id,name,globalId"),
                  false)
              .body()
              .path("docs");
      assertEquals(5, docs.size());
      for (JsonNode doc : docs) {
        JsonNode target = doc.path("target").path("value");
        long targetId = target.isNumber() ? target.asLong() : target.path("id").asLong();
        int index = rows.instrumentIds().indexOf(targetId);
        JsonNode location = doc.path("location");
        if (index == 3 || index == 4) {
          assertTrue(location.isNull(), doc.toString());
        } else {
          assertEquals("containers", location.path("relationTo").asText());
          if (depth == 0) assertTrue(location.path("value").isNumber());
          else assertTrue(location.path("value").path("name").isTextual());
          if (index == 1)
            assertEquals("IC" + rows.sharedParentId(), location.path("globalId").asText());
        }
      }
    }
  }

  @Test
  void masksUnreadableParentsAndNarrowsBookingTargets() throws Exception {
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
        assertTrue(target.path("parentContainer").isMissingNode());
      }
    }

    String where = idFilter(rows.instrumentIds());
    instrumentCount(where, null, 5);
    long withoutLocations = instrumentCount(where, null, 5);
    assertEquals(withoutLocations, instrumentCount(where, LOCATION_FIELDS, 5));
  }

  @Test
  void bookingDocumentsMatchEntityPathSnapshots() throws Exception {
    FixtureRows rows = createRows(1, "Snapshot");
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            ignored -> {
              Instrument instrument = instrumentDao.get(rows.instrumentIds().get(0));
              instrument.setSharingMode(InventorySharingMode.OWNER_ONLY);
              instrumentDao.save(instrument);
              sessionFactory
                  .getCurrentSession()
                  .createQuery(
                      "from BookingConfiguration where target.id = :id",
                      com.researchspace.model.booking.BookingConfiguration.class)
                  .setParameter("id", instrument.getId())
                  .getSingleResult()
                  .setSlotGranularityMinutes(15);
            });
    var snapshots = OBJECT_MAPPER.createObjectNode();
    for (String path : List.of("/api/v2/booking-configurations", "/api/v2/bookings")) {
      String resource = path.substring("/api/v2/".length());
      JsonNode ownPage =
          measuredRequest(
                  get(path)
                      .header("apiKey", fixture.userKey())
                      .param("where", targetFilter(rows.instrumentIds())),
                  false)
              .body();
      long documentId = ownPage.path("docs").get(0).path("id").asLong();
      for (String depth : List.of("0", "1")) {
        for (boolean sparse : List.of(false, true)) {
          for (boolean unreadableTarget : List.of(false, true)) {
            var request =
                get(path)
                    .header("apiKey", unreadableTarget ? fixture.thirdUserKey() : fixture.userKey())
                    .param("where", "id==" + documentId)
                    .param("depth", depth)
                    .param("limit", "20");
            if (sparse) request.param("fields[" + resource + "]", "id,target");
            JsonNode docs = measuredRequest(request, false).body().path("docs");
            assertEquals(unreadableTarget ? 0 : 1, docs.size());
            snapshots.set(
                resource + "-" + depth + "-" + sparse + "-" + unreadableTarget,
                normalizeSnapshot(docs));
          }
        }
      }
    }
    var snapshotPath =
        java.nio.file.Path.of("src/test/resources/api-v2/booking-entity-read-documents.json");
    if (Boolean.getBoolean("updateBookingReadSnapshot")) {
      java.nio.file.Files.createDirectories(snapshotPath.getParent());
      java.nio.file.Files.writeString(
          snapshotPath,
          OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(snapshots) + "\n");
    }
    assertEquals(OBJECT_MAPPER.readTree(java.nio.file.Files.readString(snapshotPath)), snapshots);
  }

  private static JsonNode normalizeSnapshot(JsonNode node) {
    if (node.isArray()) {
      var result = OBJECT_MAPPER.createArrayNode();
      node.forEach(value -> result.add(normalizeSnapshot(value)));
      return result;
    }
    if (node.isObject()) {
      var result = OBJECT_MAPPER.createObjectNode();
      node.properties()
          .forEach(
              entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (List.of(
                            "id",
                            "requesterId",
                            "globalId",
                            "createdAt",
                            "updatedAt",
                            "configurationVersion")
                        .contains(key)
                    || (key.equals("value") && value.isNumber())) {
                  result.set(
                      key,
                      value.isNull()
                          ? value
                          : OBJECT_MAPPER.getNodeFactory().textNode("<" + key + ">"));
                } else {
                  result.set(key, normalizeSnapshot(value));
                }
              });
      return result;
    }
    return node.isTextual()
        ? OBJECT_MAPPER
            .getNodeFactory()
            .textNode(node.asText().replaceAll("apiv2[a-z0-9]+", "<user>"))
        : node;
  }

  @Test
  void bookingEntityPagesBatchRelationshipsAndPreserveDepthAndFields() throws Exception {
    FixtureRows rows = createRows(20, "Booking page " + fixture.marker());
    for (String path : List.of("/api/v2/booking-configurations", "/api/v2/bookings")) {
      for (String depth : List.of("0", "1")) {
        String resource = path.substring("/api/v2/".length());
        var oneRequest =
            get(path)
                .header("apiKey", fixture.userKey())
                .param("where", targetFilter(rows.instrumentIds().subList(0, 1)))
                .param("depth", depth)
                .param("limit", "20");
        MeasuredResponse one = measuredRequest(oneRequest, false);
        var pageRequest =
            get(path)
                .header("apiKey", fixture.userKey())
                .param("where", targetFilter(rows.instrumentIds()))
                .param("depth", depth)
                .param("limit", "20");
        MeasuredResponse page = measuredRequest(pageRequest, false);
        assertEquals(20, page.body().path("docs").size());
        assertFalse(page.body().has("totalDocs"));
        assertTrue(
            page.statements() <= 30,
            () -> path + " depth=" + depth + " prepared " + page.statements() + " statements");
        assertTrue(
            page.statements() <= one.statements() + 4,
            () ->
                path
                    + " depth="
                    + depth
                    + " grew from "
                    + one.statements()
                    + " to "
                    + page.statements()
                    + " statements");
        JsonNode sparse =
            measuredRequest(pageRequest.param("fields[" + resource + "]", "id,target"), false)
                .body();
        for (int index = 0; index < 20; index++) {
          JsonNode fullDocument = page.body().path("docs").get(index);
          JsonNode sparseDocument = sparse.path("docs").get(index);
          assertEquals(fullDocument.path("id"), sparseDocument.path("id"));
          assertEquals(fullDocument.path("target"), sparseDocument.path("target"));
          assertEquals(2, sparseDocument.size());
          JsonNode target = sparseDocument.path("target");
          assertEquals("instruments", target.path("relationTo").asText());
          assertEquals(depth.equals("0"), target.path("value").isNumber());
        }
      }
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
                  .param("totals", "true")
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
                      .param("totals", "true")
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
      fixture.booking(instrumentId, BOOKING_START, BOOKING_END, fixture.sysadminKey());
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
    var request =
        get(path)
            .header("apiKey", fixture.userKey())
            .param("where", path.equals(INSTRUMENTS) ? idFilter(ids) : targetFilter(ids))
            .param("depth", path.equals(INSTRUMENTS) ? "1" : "2")
            .param("fields[containers]", "id,name,globalId")
            .param("limit", "100")
            .param("fields[" + path.substring("/api/v2/".length()) + "]", fields);
    if (path.equals(INSTRUMENTS)) request.param("totals", "true");
    if (!path.equals(INSTRUMENTS)) request.param("fields[instruments]", "id,name");
    MeasuredResponse response = measuredRequest(request, path.equals(INSTRUMENTS));
    assertEquals(ids.size(), response.body().path("docs").size());
    return response;
  }

  private JsonNode singleInstrument(long id, String fields) throws Exception {
    var request =
        get(INSTRUMENTS + "/" + id)
            .header("apiKey", fixture.userKey())
            .param("depth", "1")
            .param("fields[containers]", "id,name,globalId");
    if (fields != null) {
      request.param("fields[instruments]", fields);
    }
    return measuredRequest(request, true).body();
  }

  private MeasuredResponse measuredRequest(
      MockHttpServletRequestBuilder request, boolean scalarInstrumentRead) throws Exception {
    clearSession();
    statistics.clear();
    MvcResult result = mockMvc.perform(request).andReturn();
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
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
    assertTrue(
        workbench.path("parentContainer").path("value").path("globalId").asText().startsWith("BE"));

    assertParentLocation(targetNamed(body, "instrument 3"), null, null);
    assertParentLocation(targetNamed(body, "instrument 4"), null, null);
  }

  private static void assertParentLocation(JsonNode document, String name, String globalId) {
    if (name == null) {
      assertTrue(document.path("parentContainer").isNull());
    } else {
      assertEquals(name, document.path("parentContainer").path("value").path("name").asText());
      assertEquals(
          globalId, document.path("parentContainer").path("value").path("globalId").asText());
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
