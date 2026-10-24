package com.researchspace.api.v2.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.researchspace.api.v2.contract.ApiV2FilterExpansionMVCIT.CreatedInstrument;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.util.ArrayList;
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

/** Database-backed performance contract for maximum-complexity filters. */
@ApiV2WebIntegrationTest
@DisplayName("REST API v2 TableList filter expansion performance contract")
class ApiV2FilterExpansionPerformanceMVCIT {

  private static final String CONFIGURATIONS = "/api/v2/booking-configurations";
  private static final int MAX_COMPARISONS = 50;
  private static final int WARMUPS = 3;
  private static final int SAMPLES = 20;
  private static final long P95_BUDGET_MILLIS = 2_000;

  private final WebApplicationContext context;
  private final BookingConfigurationDao bookingConfigurationDao;
  private final InstrumentDao instrumentDao;
  private final InstrumentEntityApiManager instrumentApiManager;
  private final IRecordFactory recordFactory;
  private final PlatformTransactionManager transactionManager;
  private final SessionFactory sessionFactory;

  @Autowired
  ApiV2FilterExpansionPerformanceMVCIT(
      WebApplicationContext context,
      BookingConfigurationDao bookingConfigurationDao,
      InstrumentDao instrumentDao,
      InstrumentEntityApiManager instrumentApiManager,
      IRecordFactory recordFactory,
      PlatformTransactionManager transactionManager,
      SessionFactory sessionFactory) {
    this.context = context;
    this.bookingConfigurationDao = bookingConfigurationDao;
    this.instrumentDao = instrumentDao;
    this.instrumentApiManager = instrumentApiManager;
    this.recordFactory = recordFactory;
    this.transactionManager = transactionManager;
    this.sessionFactory = sessionFactory;
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
    return new MeasuredRequest(
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
        statistics.getPrepareStatementCount());
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

  private record PerformanceRow(long configurationId, long targetId) {}

  private record MeasuredRequest(long millis, long statements) {}

  private void assertListAndCountForWhere(String where, Set<Long> expected) throws Exception {
    ApiV2FilterExpansionMVCIT.assertListAndCountForWhere(mockMvc, ownerKey, where, expected);
  }

  private void assertBadRequest(String where) throws Exception {
    ApiV2FilterExpansionMVCIT.assertBadRequest(mockMvc, ownerKey, where);
  }

  private String runtimeSelector(String fieldName, String apiKey) throws Exception {
    return ApiV2FilterExpansionMVCIT.runtimeSelector(mockMvc, fieldName, apiKey);
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

  private void deleteConfigurations() {
    ApiV2FilterExpansionMVCIT.deleteConfigurations(
        transactions, bookingConfigurationDao, configurationIds);
  }

  private void deleteInstruments() {
    ApiV2FilterExpansionMVCIT.deleteInstruments(instrumentApiManager, instruments);
  }
}
