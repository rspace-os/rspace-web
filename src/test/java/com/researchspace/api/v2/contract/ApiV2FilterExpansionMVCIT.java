package com.researchspace.api.v2.contract;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v2.query.ApiV2ResourceRequestParser;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.service.BookingResourceRoleScheme;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Container;
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
import java.util.Set;
import java.util.UUID;
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
  static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final WebApplicationContext context;
  private final BookingConfigurationDao bookingConfigurationDao;
  private final InstrumentDao instrumentDao;
  private final InstrumentEntityApiManager instrumentApiManager;
  private final IRecordFactory recordFactory;
  private final PlatformTransactionManager transactionManager;
  private final SessionFactory sessionFactory;
  private final ApiV2ResourceCatalog catalog;

  private final ContainerDao containerDao;

  @Autowired
  ApiV2FilterExpansionMVCIT(
      WebApplicationContext context,
      BookingConfigurationDao bookingConfigurationDao,
      InstrumentDao instrumentDao,
      InstrumentEntityApiManager instrumentApiManager,
      IRecordFactory recordFactory,
      PlatformTransactionManager transactionManager,
      SessionFactory sessionFactory,
      ApiV2ResourceCatalog catalog,
      ContainerDao containerDao) {
    this.context = context;
    this.bookingConfigurationDao = bookingConfigurationDao;
    this.instrumentDao = instrumentDao;
    this.instrumentApiManager = instrumentApiManager;
    this.recordFactory = recordFactory;
    this.transactionManager = transactionManager;
    this.sessionFactory = sessionFactory;
    this.catalog = catalog;
    this.containerDao = containerDao;
  }

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

    // Location: the immediate parent Container, matched only where the caller may read it. An
    // unreadable Container behaves exactly like one that does not exist, for rows and totals.
    ApiContainer room = fixture.container(owner, "cold room " + fixture.marker());
    long locatedTarget = fixture.instrumentIn(owner, "located " + fixture.marker(), room);
    ApiContainer privateRoom = fixture.container(inaccessibleOwner, "vault " + fixture.marker());
    long hiddenTarget =
        fixture.instrumentIn(inaccessibleOwner, "vaulted " + fixture.marker(), privateRoom);
    makePrivate(hiddenTarget);
    makeContainerPrivate(privateRoom.getId());
    long locatedConfig = configuration(locatedTarget, owner);
    long hiddenConfig = configuration(hiddenTarget, owner);
    List<Long> located =
        List.of(matchingConfig, otherConfig, inaccessibleConfig, locatedConfig, hiddenConfig);
    String missing = "IC90000000000000";
    long workbench = parentId(matchingTarget);

    assertListAndCount(located, "location==IC" + room.getId(), Set.of(locatedConfig));
    assertListAndCount(located, "location.value==" + room.getId(), Set.of(locatedConfig));
    assertListAndCount(located, "location==IC" + privateRoom.getId(), Set.of());
    assertListAndCount(located, "location==" + missing, Set.of());
    Set<Long> readable = Set.of(matchingConfig, otherConfig, locatedConfig);
    assertListAndCount(located, "location!=IC" + privateRoom.getId(), readable);
    assertListAndCount(located, "location!=" + missing, readable);
    assertListAndCount(
        located,
        "location=in=(IC" + room.getId() + ",IC" + privateRoom.getId() + ")",
        Set.of(locatedConfig));
    // A workbench shares the Container ID space, so IC plus its ID names it too.
    assertListAndCount(located, "location==IC" + workbench, Set.of(matchingConfig, otherConfig));
    assertListAndCount(located, "location=exists=true", readable);
    assertListAndCount(located, "location=exists=false", Set.of());
    assertBadRequest("id=in=(" + join(located) + ");location==BE" + workbench);
    assertLocations("globalId=IC" + privateRoom.getId(), List.of());
    assertLocations("q=vault " + fixture.marker(), List.of());
    assertLocations("q=IC" + privateRoom.getId(), List.of());
    assertLocations("globalId=IC" + room.getId(), List.of("IC" + room.getId()));
    assertLocations("q=BE" + workbench, List.of("BE" + workbench));
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

  private void assertListAndCount(List<Long> scopedIds, String filter, Set<Long> expected)
      throws Exception {
    String where = "id=in=(" + join(scopedIds) + ");" + filter;
    assertListAndCountForWhere(where, expected);
  }

  static void assertListAndCountForWhere(
      MockMvc mockMvc, String ownerKey, String where, Set<Long> expected) throws Exception {
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
            registration.runtimeFieldContext(owner));
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

  static void assertBadRequest(MockMvc mockMvc, String ownerKey, String where) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(CONFIGURATIONS).header("apiKey", ownerKey).param("where", where))
            .andReturn();
    assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
  }

  static String runtimeSelector(MockMvc mockMvc, String fieldName, String apiKey) throws Exception {
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

  static long instrument(
      ApiV2Fixture fixture,
      List<CreatedInstrument> instruments,
      User instrumentOwner,
      String name) {
    long id = fixture.instrument(instrumentOwner, name);
    instruments.add(new CreatedInstrument(id, instrumentOwner));
    return id;
  }

  static void addExtraField(
      TransactionTemplate transactions,
      InstrumentDao instrumentDao,
      IRecordFactory recordFactory,
      SessionFactory sessionFactory,
      long instrumentId,
      User fieldOwner,
      String name,
      String value) {
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

  static void makePrivate(
      TransactionTemplate transactions, InstrumentDao instrumentDao, long instrumentId) {
    transactions.executeWithoutResult(
        ignored -> {
          Instrument instrument = instrumentDao.get(instrumentId);
          instrument.setSharingMode(InventorySharingMode.OWNER_ONLY);
          instrumentDao.save(instrument);
        });
  }

  static long configuration(
      TransactionTemplate transactions,
      SessionFactory sessionFactory,
      List<Long> configurationIds,
      long targetId,
      User configurationOwner) {
    return configuration(
        transactions,
        sessionFactory,
        configurationIds,
        targetId,
        configurationOwner,
        configurationOwner);
  }

  static long configuration(
      TransactionTemplate transactions,
      SessionFactory sessionFactory,
      List<Long> configurationIds,
      long targetId,
      User configurationOwner,
      User creator) {
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

  static void deleteConfigurations(
      TransactionTemplate transactions,
      BookingConfigurationDao bookingConfigurationDao,
      List<Long> configurationIds) {
    transactions.executeWithoutResult(
        ignored ->
            configurationIds.forEach(
                id ->
                    bookingConfigurationDao
                        .lockById(id)
                        .ifPresent(bookingConfigurationDao::removeConfigurationAndAccess)));
    configurationIds.clear();
  }

  static void deleteInstruments(
      InstrumentEntityApiManager instrumentApiManager, List<CreatedInstrument> instruments) {
    for (CreatedInstrument instrument : instruments) {
      instrumentApiManager.markInstrumentAsDeleted(instrument.id(), instrument.owner());
    }
    instruments.clear();
  }

  static String join(List<Long> ids) {
    return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
  }

  record CreatedInstrument(long id, User owner) {}

  private void assertListAndCountForWhere(String where, Set<Long> expected) throws Exception {
    ApiV2FilterExpansionMVCIT.assertListAndCountForWhere(mockMvc, ownerKey, where, expected);
  }

  private void assertLocations(String query, List<String> expectedGlobalIds) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v2/booking-catalogue/locations?" + query.replace(" ", "%20"))
                    .header("apiKey", ownerKey))
            .andReturn();
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    JsonNode body = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    List<String> actual = new ArrayList<>();
    body.path("items").forEach(item -> actual.add(item.path("globalId").asText()));
    assertEquals(expectedGlobalIds, actual, "locations for " + query);
    assertEquals(expectedGlobalIds.size(), body.path("total").asLong(), "location total");
  }

  private long parentId(long instrumentId) {
    return transactions.execute(
        ignored ->
            instrumentDao
                .getParentLocationSummaries(Set.of(instrumentId))
                .get(instrumentId)
                .containerId());
  }

  private void makeContainerPrivate(long containerId) {
    transactions.executeWithoutResult(
        ignored -> {
          Container container = containerDao.get(containerId);
          container.setSharingMode(InventorySharingMode.OWNER_ONLY);
          containerDao.save(container);
        });
  }

  private void assertBadRequest(String where) throws Exception {
    ApiV2FilterExpansionMVCIT.assertBadRequest(mockMvc, ownerKey, where);
  }

  private String runtimeSelector(String fieldName, String apiKey) throws Exception {
    return ApiV2FilterExpansionMVCIT.runtimeSelector(mockMvc, fieldName, apiKey);
  }

  private void makePrivate(long instrumentId) {
    ApiV2FilterExpansionMVCIT.makePrivate(transactions, instrumentDao, instrumentId);
  }

  private long instrument(User instrumentOwner, String name) {
    return ApiV2FilterExpansionMVCIT.instrument(fixture, instruments, instrumentOwner, name);
  }

  private void addExtraField(long instrumentId, User fieldOwner, String name, String value) {
    ApiV2FilterExpansionMVCIT.addExtraField(
        transactions,
        instrumentDao,
        recordFactory,
        sessionFactory,
        instrumentId,
        fieldOwner,
        name,
        value);
  }

  private long configuration(long targetId, User configurationOwner) {
    return ApiV2FilterExpansionMVCIT.configuration(
        transactions, sessionFactory, configurationIds, targetId, configurationOwner);
  }

  private long configuration(long targetId, User configurationOwner, User creator) {
    return ApiV2FilterExpansionMVCIT.configuration(
        transactions, sessionFactory, configurationIds, targetId, configurationOwner, creator);
  }

  private void deleteConfigurations() {
    ApiV2FilterExpansionMVCIT.deleteConfigurations(
        transactions, bookingConfigurationDao, configurationIds);
  }

  private void deleteInstruments() {
    ApiV2FilterExpansionMVCIT.deleteInstruments(instrumentApiManager, instruments);
  }
}
