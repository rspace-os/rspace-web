package com.researchspace.api.v2.contract;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.query.ApiV2ResourceRequestParser;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.service.BookingResourceRoleScheme;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** Database-backed contract for relationship identity and one-hop runtime filters. */
@ApiV2WebIntegrationTest
@DisplayName("REST API v2 TableList filter expansion contract")
class ApiV2FilterExpansionMVCIT {

  private static final String CONFIGURATIONS = "/api/v2/booking-configurations";
  private static final String RUNTIME_FIELDS = "/api/v2/instruments/fields/extraFields";
  private static final int MAX_COMPARISONS = 50;
  private static final int WARMUPS = 3;
  private static final int SAMPLES = 20;
  private static final long P95_BUDGET_MILLIS = 2_000;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private BookingConfigurationDao bookingConfigurationDao;
  @Autowired private InstrumentDao instrumentDao;
  @Autowired private InstrumentEntityApiManager instrumentApiManager;
  @Autowired private IRecordFactory recordFactory;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private SessionFactory sessionFactory;
  @Autowired private ApiV2ResourceCatalog catalog;

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;
  private TransactionTemplate transactions;
  private Statistics statistics;
  private final List<Long> configurationIds = new ArrayList<>();
  private final List<CreatedInstrument> instruments = new ArrayList<>();
  private User owner;
  private User inaccessibleOwner;
  private String ownerKey;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
    fixture.enableBookings();
    transactions = new TransactionTemplate(transactionManager);
    statistics = sessionFactory.getStatistics();
    statistics.setStatisticsEnabled(true);
    owner = fixture.user();
    ownerKey = fixture.userKey();
    inaccessibleOwner = fixture.otherUser();
  }

  @AfterEach
  void tearDown() {
    try {
      deleteConfigurations();
      deleteInstruments();
    } finally {
      if (statistics != null) {
        statistics.clear();
        statistics.setStatisticsEnabled(false);
      }
      fixture.cleanUp();
    }
  }

  @Test
  @DisplayName("identity filters do not reveal missing or unreadable relationship targets")
  void relationshipIdentityFiltersHaveNoReadableTargetExistenceOracle() throws Exception {
    long matchingTarget = instrument(owner, "matching " + fixture.marker());
    long otherTarget = instrument(owner, "other " + fixture.marker());
    long inaccessibleTarget = instrument(inaccessibleOwner, "private " + fixture.marker());
    makePrivate(inaccessibleTarget);

    long matchingConfig = configuration(matchingTarget, owner);
    long otherConfig = configuration(otherTarget, owner);
    long inaccessibleConfig = configuration(inaccessibleTarget, owner);
    long missingConfig = configuration(90_000_000_000_000L, owner);
    List<Long> scoped = List.of(matchingConfig, otherConfig, inaccessibleConfig, missingConfig);

    assertListAndCount(scoped, "target=exists=true", Set.of(matchingConfig, otherConfig));
    assertListAndCount(scoped, "target=exists=false", Set.of());
    // The HTTP source policy requires a readable target. This direct DAO check omits that root
    // constraint while retaining the real target read policy, so it verifies that false means
    // missing or unreadable without making those configurations visible through the endpoint.
    assertDirectListAndCount(
        scoped, "target=exists=false", Set.of(inaccessibleConfig, missingConfig));
    assertListAndCount(scoped, "target==IN" + matchingTarget, Set.of(matchingConfig));
    assertListAndCount(
        scoped,
        "target=in=(IN" + matchingTarget + ",IN" + otherTarget + ")",
        Set.of(matchingConfig, otherConfig));
    assertListAndCount(scoped, "target!=IN" + matchingTarget, Set.of(otherConfig));
    assertListAndCount(
        scoped, "target=out=(IN" + matchingTarget + ",IN" + otherTarget + ")", Set.of());

    // The legacy typed numeric and relation-kind selectors stay queryable beside the global ID.
    assertListAndCount(scoped, "target.value==" + matchingTarget, Set.of(matchingConfig));
    assertListAndCount(scoped, "target.value=exists=false", Set.of());
    assertListAndCount(
        scoped, "target.relationTo==booking-instruments", Set.of(matchingConfig, otherConfig));
    assertBadRequest("id=in=(" + join(scoped) + ");target==XX" + matchingTarget);
  }

  @Test
  @DisplayName("scalar user relationship filters constrain the page total")
  void scalarUserRelationshipFilterKeepsPageTotalInSync() throws Exception {
    long firstMatchingTarget = instrument(owner, "creator match one " + fixture.marker());
    long secondMatchingTarget = instrument(owner, "creator match two " + fixture.marker());
    long firstOtherTarget = instrument(owner, "creator other one " + fixture.marker());
    long secondOtherTarget = instrument(owner, "creator other two " + fixture.marker());
    long firstMatching = configuration(firstMatchingTarget, owner, owner);
    long secondMatching = configuration(secondMatchingTarget, owner, owner);
    long firstOther = configuration(firstOtherTarget, owner, inaccessibleOwner);
    long secondOther = configuration(secondOtherTarget, owner, inaccessibleOwner);
    List<Long> scoped = List.of(firstMatching, secondMatching, firstOther, secondOther);
    String filter = "createdBy.username=contains=" + owner.getUsername();

    assertListAndCount(scoped, filter, Set.of(firstMatching, secondMatching));

    String where = "id=in=(" + join(scoped) + ");" + filter;
    assertListPage(where, 1, Set.of(firstMatching), 2);
    assertListPage(where, 2, Set.of(secondMatching), 2);
  }

  @Test
  @DisplayName(
      "hopped runtime existence excludes unreadable targets and includes empty readable values")
  void relationshipRuntimeFiltersMatchTheirPresenceTruthTable() throws Exception {
    String fieldName = "Filter expansion " + UUID.randomUUID();
    long noFieldTarget = instrument(owner, "no field " + fixture.marker());
    long nullTarget = instrument(owner, "null field " + fixture.marker());
    long emptyTarget = instrument(owner, "empty field " + fixture.marker());
    long otherTarget = instrument(owner, "other field " + fixture.marker());
    long matchingTarget = instrument(owner, "matching field " + fixture.marker());
    long inaccessibleTarget = instrument(inaccessibleOwner, "private field " + fixture.marker());
    makePrivate(inaccessibleTarget);

    addExtraField(nullTarget, owner, fieldName, null);
    addExtraField(emptyTarget, owner, fieldName, "");
    addExtraField(otherTarget, owner, fieldName, "BSL-1");
    addExtraField(matchingTarget, owner, fieldName, "BSL-2");
    addExtraField(inaccessibleTarget, inaccessibleOwner, fieldName, "BSL-2");

    long noFieldConfig = configuration(noFieldTarget, owner);
    long nullConfig = configuration(nullTarget, owner);
    long emptyConfig = configuration(emptyTarget, owner);
    long otherConfig = configuration(otherTarget, owner);
    long matchingConfig = configuration(matchingTarget, owner);
    long inaccessibleConfig = configuration(inaccessibleTarget, owner);
    long missingConfig = configuration(90_000_000_000_001L, owner);
    List<Long> scoped =
        List.of(
            noFieldConfig,
            nullConfig,
            emptyConfig,
            otherConfig,
            matchingConfig,
            inaccessibleConfig,
            missingConfig);
    String selector = runtimeSelector(fieldName, ownerKey);
    String runtime = "target." + selector;

    assertListAndCount(scoped, runtime + "=exists=true", Set.of(otherConfig, matchingConfig));
    assertListAndCount(
        scoped, runtime + "=exists=false", Set.of(noFieldConfig, nullConfig, emptyConfig));
    assertListAndCount(scoped, runtime + "==BSL-2", Set.of(matchingConfig));
    assertListAndCount(scoped, runtime + "!=BSL-2", Set.of(otherConfig));
  }

  @Test
  @DisplayName("maximum filter complexity has bounded SQL count and p95 as matching rows grow")
  void maximumPredicateWorkloadScalesFromOneToTwentyFiveRows() throws Exception {
    String fieldName = "Filter expansion performance " + UUID.randomUUID();
    List<PerformanceRow> rows = new ArrayList<>();
    for (int index = 0; index < 100; index++) {
      long targetId = instrument(owner, "performance " + index + " " + fixture.marker());
      addExtraField(targetId, owner, fieldName, "SCOPE-2");
      long configId = configuration(targetId, owner);
      rows.add(new PerformanceRow(configId, targetId));
    }

    String selector = runtimeSelector(fieldName, ownerKey);
    List<MeasuredRequest> oneRowList = new ArrayList<>();
    List<MeasuredRequest> oneRowCount = new ArrayList<>();
    List<MeasuredRequest> twentyFiveRowList = new ArrayList<>();
    List<MeasuredRequest> twentyFiveRowCount = new ArrayList<>();

    String oneRowWhere = maximumWhere(rows.subList(0, 1), selector);
    String twentyFiveRowWhere = maximumWhere(rows.subList(0, 25), selector);
    assertEquals(MAX_COMPARISONS, comparisonCount(oneRowWhere));
    assertEquals(MAX_COMPARISONS, comparisonCount(twentyFiveRowWhere));
    assertListAndCountForWhere(oneRowWhere, Set.of(rows.get(0).configurationId()));
    assertListAndCountForWhere(
        twentyFiveRowWhere,
        rows.subList(0, 25).stream()
            .map(PerformanceRow::configurationId)
            .collect(java.util.stream.Collectors.toSet()));
    assertBadRequest(twentyFiveRowWhere + ";enabled==true");

    for (int warmup = 0; warmup < WARMUPS; warmup++) {
      measured(oneRowWhere, false, ownerKey);
      measured(oneRowWhere, true, ownerKey);
      measured(twentyFiveRowWhere, false, ownerKey);
      measured(twentyFiveRowWhere, true, ownerKey);
    }
    for (int sample = 0; sample < SAMPLES; sample++) {
      oneRowList.add(measured(oneRowWhere, false, ownerKey));
      oneRowCount.add(measured(oneRowWhere, true, ownerKey));
      twentyFiveRowList.add(measured(twentyFiveRowWhere, false, ownerKey));
      twentyFiveRowCount.add(measured(twentyFiveRowWhere, true, ownerKey));
    }

    System.out.printf(
        Locale.ROOT,
        "API_V2_FILTER_EXPANSION|oneRowListP95Ms=%d|oneRowCountP95Ms=%d|"
            + "twentyFiveRowListP95Ms=%d|twentyFiveRowCountP95Ms=%d|"
            + "oneRowListStatements=%d|twentyFiveRowListStatements=%d|"
            + "oneRowCountStatements=%d|twentyFiveRowCountStatements=%d%n",
        p95Millis(oneRowList),
        p95Millis(oneRowCount),
        p95Millis(twentyFiveRowList),
        p95Millis(twentyFiveRowCount),
        medianStatements(oneRowList),
        medianStatements(twentyFiveRowList),
        medianStatements(oneRowCount),
        medianStatements(twentyFiveRowCount));

    assertTrue(
        p95Millis(oneRowList) <= P95_BUDGET_MILLIS,
        () -> "one-row list p95 was " + p95Millis(oneRowList) + "ms");
    assertTrue(
        p95Millis(oneRowCount) <= P95_BUDGET_MILLIS,
        () -> "one-row count p95 was " + p95Millis(oneRowCount) + "ms");
    assertTrue(
        p95Millis(twentyFiveRowList) <= P95_BUDGET_MILLIS,
        () -> "25-row list p95 was " + p95Millis(twentyFiveRowList) + "ms");
    assertTrue(
        p95Millis(twentyFiveRowCount) <= P95_BUDGET_MILLIS,
        () -> "25-row count p95 was " + p95Millis(twentyFiveRowCount) + "ms");
    assertTrue(
        medianStatements(twentyFiveRowList) <= medianStatements(oneRowList) + 1,
        () ->
            "25-row list used "
                + medianStatements(twentyFiveRowList)
                + " SQL statements versus "
                + medianStatements(oneRowList)
                + " for one row");
    assertTrue(
        medianStatements(twentyFiveRowCount) <= medianStatements(oneRowCount) + 1,
        () ->
            "25-row count used "
                + medianStatements(twentyFiveRowCount)
                + " SQL statements versus "
                + medianStatements(oneRowCount)
                + " for one row");
  }

  private void assertListAndCount(List<Long> scopedIds, String filter, Set<Long> expected)
      throws Exception {
    String where = "id=in=(" + join(scopedIds) + ");" + filter;
    assertListAndCountForWhere(where, expected);
  }

  private void assertListAndCountForWhere(String where, Set<Long> expected) throws Exception {
    MvcResult listed =
        mockMvc
            .perform(
                get(CONFIGURATIONS)
                    .header("apiKey", ownerKey)
                    .param("where", where)
                    .param("limit", "100")
                    .param("fields[booking-configurations]", "id"))
            .andReturn();
    assertEquals(
        200,
        listed.getResponse().getStatus(),
        "list failed for where=" + where + " " + listed.getResponse().getContentAsString());
    JsonNode listBody = OBJECT_MAPPER.readTree(listed.getResponse().getContentAsString());
    Set<Long> actual = new HashSet<>();
    listBody.path("docs").forEach(document -> actual.add(document.path("id").asLong()));

    MvcResult counted =
        mockMvc
            .perform(
                get(CONFIGURATIONS + "/count").header("apiKey", ownerKey).param("where", where))
            .andReturn();
    assertEquals(
        200, counted.getResponse().getStatus(), counted.getResponse().getContentAsString());
    JsonNode countBody = OBJECT_MAPPER.readTree(counted.getResponse().getContentAsString());

    assertAll(
        "filter results for " + where,
        () -> assertEquals(expected, actual, "list result set"),
        () -> assertEquals(expected.size(), listBody.path("totalDocs").asLong(), "list total"),
        () -> assertEquals(expected.size(), countBody.path("totalDocs").asLong(), "count total"));
  }

  private void assertListPage(String where, int page, Set<Long> expected, long total)
      throws Exception {
    MvcResult listed =
        mockMvc
            .perform(
                get(CONFIGURATIONS)
                    .header("apiKey", ownerKey)
                    .param("where", where)
                    .param("limit", "1")
                    .param("page", Integer.toString(page))
                    .param("fields[booking-configurations]", "id"))
            .andReturn();
    assertEquals(
        200,
        listed.getResponse().getStatus(),
        "list failed for page="
            + page
            + " where="
            + where
            + " "
            + listed.getResponse().getContentAsString());
    JsonNode body = OBJECT_MAPPER.readTree(listed.getResponse().getContentAsString());
    Set<Long> actual = new HashSet<>();
    body.path("docs").forEach(document -> actual.add(document.path("id").asLong()));
    assertAll(
        "page " + page + " for " + where,
        () -> assertEquals(expected, actual, "page result set"),
        () -> assertEquals(total, body.path("totalDocs").asLong(), "page total"));
  }

  private void assertDirectListAndCount(List<Long> scopedIds, String filter, Set<Long> expected) {
    String where = "id=in=(" + join(scopedIds) + ");" + filter;
    var registration = catalog.find("booking-configurations").orElseThrow();
    ResourceRequest parsed =
        ApiV2ResourceRequestParser.filtered(
            where,
            registration.description(),
            catalog.registry(),
            registration.runtimeFieldContext(owner, catalog::runtimeFieldsOf));
    ResourceRequest request =
        new ResourceRequest(
            parsed.filter(),
            parsed.serverConstraint(),
            parsed.sort(),
            new ResourceRequest.Page(1, 100),
            parsed.fieldSelections(),
            parsed.includes(),
            parsed.runtime());
    var targetAccess = RelationshipReadAccess.forActor(catalog.registry(), owner);
    ResourcePage<BookingConfiguration> page =
        transactions.execute(
            ignored -> bookingConfigurationDao.getResources(request, targetAccess));
    long count =
        transactions.execute(
            ignored -> bookingConfigurationDao.countResources(request, targetAccess));
    Set<Long> actual =
        page.resources().stream()
            .map(BookingConfiguration::getId)
            .collect(java.util.stream.Collectors.toSet());
    assertEquals(expected, actual, "direct query result set for " + filter);
    assertEquals(expected.size(), page.total(), "direct page count for " + filter);
    assertEquals(expected.size(), count, "direct count for " + filter);
  }

  private void assertBadRequest(String where) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(CONFIGURATIONS).header("apiKey", ownerKey).param("where", where))
            .andReturn();
    assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
  }

  private String runtimeSelector(String fieldName, String apiKey) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(RUNTIME_FIELDS)
                    .header("apiKey", apiKey)
                    .param("search", fieldName)
                    .param("limit", "50"))
            .andReturn();
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    JsonNode fields =
        OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).path("fields");
    for (JsonNode field : fields) {
      if (fieldName.equals(field.path("label").asText())) {
        return field.path("selector").asText();
      }
    }
    throw new AssertionError("Runtime field catalog omitted fixture field " + fieldName);
  }

  private long instrument(User instrumentOwner, String name) {
    long id = fixture.instrument(instrumentOwner, name);
    instruments.add(new CreatedInstrument(id, instrumentOwner));
    return id;
  }

  private void addExtraField(long instrumentId, User fieldOwner, String name, String value) {
    transactions.executeWithoutResult(
        ignored -> {
          Instrument instrument = instrumentDao.get(instrumentId);
          ExtraField field =
              recordFactory.createExtraField(name, FieldType.TEXT, fieldOwner, instrument);
          field.setData(value);
          instrument.addExtraField(field);
          instrumentDao.save(instrument);
          sessionFactory.getCurrentSession().flush();
        });
  }

  private void makePrivate(long instrumentId) {
    transactions.executeWithoutResult(
        ignored -> {
          Instrument instrument = instrumentDao.get(instrumentId);
          instrument.setSharingMode(InventorySharingMode.OWNER_ONLY);
          instrumentDao.save(instrument);
        });
  }

  private long configuration(long targetId, User configurationOwner) {
    return configuration(targetId, configurationOwner, configurationOwner);
  }

  private long configuration(long targetId, User configurationOwner, User creator) {
    return transactions.execute(
        ignored -> {
          BookingConfiguration configuration = new BookingConfiguration();
          configuration.setEnabled(true);
          configuration.setTimeZone("UTC");
          configuration.setCreatedBy(creator);
          configuration.setUpdatedBy(creator);
          configuration.replaceTarget(
              new BookableTargetReference(BookableTargetType.INSTRUMENT, targetId));
          ResourceAccess access =
              new ResourceAccess(
                  BookingResourceRoleScheme.SCHEME_KEY, configurationOwner, new Date());
          access.addAssignment(
              ResourceRoleAssignment.forUser(BookingResourceRoleScheme.OWNER, configurationOwner));
          configuration.setResourceAccess(access);
          sessionFactory.getCurrentSession().persist(configuration);
          sessionFactory.getCurrentSession().flush();
          long id = configuration.getId();
          configurationIds.add(id);
          return id;
        });
  }

  private void deleteConfigurations() {
    transactions.executeWithoutResult(
        ignored ->
            configurationIds.forEach(
                id ->
                    bookingConfigurationDao
                        .lockById(id)
                        .ifPresent(bookingConfigurationDao::removeConfigurationAndAccess)));
    configurationIds.clear();
  }

  private void deleteInstruments() {
    for (CreatedInstrument instrument : instruments) {
      instrumentApiManager.markInstrumentAsDeleted(instrument.id(), instrument.owner());
    }
    instruments.clear();
  }

  private String maximumWhere(List<PerformanceRow> rows, String runtimeSelector) {
    String ids =
        rows.stream()
            .map(row -> Long.toString(row.configurationId()))
            .collect(java.util.stream.Collectors.joining(","));
    String targets =
        rows.stream()
            .map(row -> "IN" + row.targetId())
            .collect(java.util.stream.Collectors.joining(","));
    String where =
        "id=in=(" + ids + ");target=in=(" + targets + ");target." + runtimeSelector + "==SCOPE-2";
    return where + (";target." + runtimeSelector + "==SCOPE-2").repeat(MAX_COMPARISONS - 3);
  }

  private int comparisonCount(String where) {
    return where.split(";").length;
  }

  private MeasuredRequest measured(String where, boolean count, String apiKey) throws Exception {
    clearSession();
    statistics.clear();
    long started = System.nanoTime();
    String path = count ? CONFIGURATIONS + "/count" : CONFIGURATIONS;
    var request = get(path).header("apiKey", apiKey).param("where", where);
    if (!count) {
      request.param("limit", "25").param("fields[booking-configurations]", "id");
    }
    MvcResult result = mockMvc.perform(request).andReturn();
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    JsonNode body = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    return new MeasuredRequest(
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
        statistics.getPrepareStatementCount(),
        body.path("totalDocs").asLong());
  }

  private void clearSession() {
    transactions.executeWithoutResult(
        ignored -> {
          sessionFactory.getCurrentSession().flush();
          sessionFactory.getCurrentSession().clear();
        });
  }

  private static long p95Millis(List<MeasuredRequest> samples) {
    List<Long> sorted = samples.stream().map(MeasuredRequest::millis).sorted().toList();
    int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
    return sorted.get(index);
  }

  private static long medianStatements(List<MeasuredRequest> samples) {
    List<Long> sorted = samples.stream().map(MeasuredRequest::statements).sorted().toList();
    return sorted.get(sorted.size() / 2);
  }

  private static String join(List<Long> ids) {
    return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
  }

  private record CreatedInstrument(long id, User owner) {}

  private record PerformanceRow(long configurationId, long targetId) {}

  private record MeasuredRequest(long millis, long statements, long totalDocs) {}
}
