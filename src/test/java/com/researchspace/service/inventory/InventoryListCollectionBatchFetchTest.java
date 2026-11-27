package com.researchspace.service.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Inventory list pages read the barcodes, identifiers and attachments of every listed record. These
 * collections must be batch-fetched, so the number of collection loads does not grow with the page
 * size.
 */
class InventoryListCollectionBatchFetchTest extends SpringTransactionalTest {

  /** More than the batch size of 20, so each list role needs two batches. */
  private static final int RECORD_COUNT = 25;

  private static final List<String> LIST_COLLECTIONS = List.of("barcodes", "identifiers", "files");

  private User testUser;
  private Statistics stats;

  @BeforeEach
  void createUserAndStatistics() {
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("batch"));
    stats = sessionFactory.getStatistics();
  }

  @Test
  void containerContentPageBatchesCollectionLoads() {
    for (int i = 0; i < RECORD_COUNT; i++) {
      ApiContainer container = new ApiContainer("c" + i, Container.ContainerType.LIST);
      container.getBarcodes().add(new ApiBarcode("container-barcode-" + i));
      containerApiMgr.createNewApiContainer(container, testUser);
    }
    Long workbenchId = containerApiMgr.getWorkbenchIdForUser(testUser);

    ApiInventorySearchResult result =
        countingCollectionLoads(
            () ->
                containerApiMgr.searchForContentOfContainer(
                    workbenchId, null, null, inventoryPage(), testUser));

    assertThat(result.getRecords()).hasSize(RECORD_COUNT);
    assertThat(result.getRecords()).allSatisfy(r -> assertThat(r.getBarcodes()).hasSize(1));
    // the workbench loads before its contents, so its identifiers and files take a batch of their
    // own; container barcodes load only when the page is converted, so the workbench's join a batch
    assertCollectionFetches(
        Container.class, Map.of("barcodes", 2L, "identifiers", 3L, "files", 3L));
  }

  @Test
  void containerContentShortPageLeavesOffPageBarcodesUnloaded() {
    int containerCount = 45;
    for (int i = 0; i < containerCount; i++) {
      ApiContainer container = new ApiContainer("c" + i, Container.ContainerType.LIST);
      container.getBarcodes().add(new ApiBarcode("container-barcode-" + i));
      containerApiMgr.createNewApiContainer(container, testUser);
    }
    Long workbenchId = containerApiMgr.getWorkbenchIdForUser(testUser);
    PaginationCriteria<InventoryRecord> shortPage =
        PaginationCriteria.createDefaultForClass(InventoryRecord.class);
    shortPage.setResultsPerPage(5);

    ApiInventorySearchResult result =
        countingCollectionLoads(
            () ->
                containerApiMgr.searchForContentOfContainer(
                    workbenchId, null, null, shortPage, testUser));

    assertThat(result.getRecords()).hasSize(5);
    assertThat(result.getRecords()).allSatisfy(r -> assertThat(r.getBarcodes()).hasSize(1));
    // a batch may load off-page barcodes along with the page's, but not every container's
    assertThat(
            stats.getCollectionStatistics(Container.class.getName() + ".barcodes").getLoadCount())
        .isLessThan(containerCount);
  }

  @Test
  void subSamplePageBatchesCollectionLoads() {
    List<ApiSubSample> subSamples = new ArrayList<>();
    for (int i = 0; i < RECORD_COUNT; i++) {
      ApiSubSample subSample = new ApiSubSample("ss" + i);
      subSample.setQuantity(new ApiQuantityInfo(BigDecimal.ONE, RSUnitDef.GRAM));
      subSample.getBarcodes().add(new ApiBarcode("subsample-barcode-" + i));
      subSamples.add(subSample);
    }
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("sample");
    newSample.setSubSamples(subSamples);
    Long sampleId = sampleApiMgr.createNewApiSample(newSample, testUser).getId();

    ApiInventorySearchResult result =
        countingCollectionLoads(
            () ->
                sampleApiMgr.searchSubSamplesBySampleId(
                    sampleId,
                    null,
                    InventorySearchDeletedOption.EXCLUDE,
                    inventoryPage(),
                    testUser));

    assertThat(result.getRecords()).hasSize(RECORD_COUNT);
    assertThat(result.getRecords()).allSatisfy(r -> assertThat(r.getBarcodes()).hasSize(1));
    assertListCollectionsBatched(SubSample.class, 2);
  }

  @Test
  void samplePageBatchesCollectionLoads() {
    for (int i = 0; i < RECORD_COUNT; i++) {
      ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples("s" + i);
      sample.getBarcodes().add(new ApiBarcode("sample-barcode-" + i));
      sampleApiMgr.createNewApiSample(sample, testUser);
    }
    PaginationCriteria<Sample> pgCrit = PaginationCriteria.createDefaultForClass(Sample.class);
    pgCrit.setResultsPerPage(RECORD_COUNT);

    ApiSampleSearchResult result =
        countingCollectionLoads(
            () ->
                sampleApiMgr.getSamplesForUser(
                    pgCrit, null, InventorySearchDeletedOption.EXCLUDE, testUser));

    assertThat(result.getSamples()).hasSize(RECORD_COUNT);
    assertThat(result.getSamples()).allSatisfy(r -> assertThat(r.getBarcodes()).hasSize(1));
    assertListCollectionsBatched(SampleEntity.class, 2);
  }

  @Test
  void instrumentPageBatchesCollectionLoads() {
    for (int i = 0; i < RECORD_COUNT; i++) {
      ApiInstrument instrument = new ApiInstrument();
      instrument.setName("i" + i);
      instrument.getBarcodes().add(new ApiBarcode("instrument-barcode-" + i));
      instrumentApiMgr.createNewApiInstrument(instrument, testUser);
    }
    PaginationCriteria<Instrument> pgCrit =
        PaginationCriteria.createDefaultForClass(Instrument.class);
    pgCrit.setResultsPerPage(RECORD_COUNT);

    ApiInventorySearchResult result =
        countingCollectionLoads(
            () ->
                instrumentApiMgr.searchInstrumentsForUser(
                    pgCrit, null, InventorySearchDeletedOption.EXCLUDE, null, testUser));

    assertThat(result.getRecords()).hasSize(RECORD_COUNT);
    assertThat(result.getRecords())
        .extracting(ApiInventoryRecordInfo::getBarcodes)
        .allSatisfy(barcodes -> assertThat(barcodes).hasSize(1));
    assertListCollectionsBatched(InstrumentEntity.class, 2);
  }

  private PaginationCriteria<InventoryRecord> inventoryPage() {
    PaginationCriteria<InventoryRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(InventoryRecord.class);
    pgCrit.setResultsPerPage(RECORD_COUNT);
    return pgCrit;
  }

  /** Runs the listing against an empty session, so every record is loaded from the database. */
  private <T> T countingCollectionLoads(Supplier<T> listing) {
    sessionFactory.getCurrentSession().flush();
    clearSessionAndEvictAll();
    boolean statisticsWereEnabled = stats.isStatisticsEnabled();
    stats.clear();
    stats.setStatisticsEnabled(true);
    try {
      return listing.get();
    } finally {
      stats.setStatisticsEnabled(statisticsWereEnabled);
    }
  }

  private void assertListCollectionsBatched(Class<?> owner, long expectedFetchesPerRole) {
    Map<String, Long> expected = new TreeMap<>();
    for (String collection : LIST_COLLECTIONS) {
      expected.put(collection, expectedFetchesPerRole);
    }
    assertCollectionFetches(owner, expected);
  }

  private void assertCollectionFetches(Class<?> owner, Map<String, Long> expectedByCollection) {
    Map<String, Long> fetchesByCollection = new TreeMap<>();
    for (String collection : LIST_COLLECTIONS) {
      String role = owner.getName() + "." + collection;
      fetchesByCollection.put(collection, stats.getCollectionStatistics(role).getFetchCount());
    }
    assertEquals(new TreeMap<>(expectedByCollection), fetchesByCollection);
  }
}
