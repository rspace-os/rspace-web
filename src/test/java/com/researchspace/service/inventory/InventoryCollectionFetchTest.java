package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.axiope.search.SearchUtils;
import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryCollectionFetchTest extends SpringTransactionalTest {

  private User user;

  @BeforeEach
  void setUpInventory() {
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("inventoryFetch"));
    initialiseContentWithEmptyContent(user);
  }

  @Test
  void fetchingOnePageDoesNotFetchMoreMetadataForLargerContainers() {
    ApiContainer small = createContainerWithIndependentSamples(2);
    ApiContainer large = createContainerWithIndependentSamples(8);
    PaginationCriteria<InventoryRecord> page =
        PaginationCriteria.createDefaultForClass(InventoryRecord.class);
    page.setResultsPerPage(1);

    assertEquals(
        collectionFetches(
            () ->
                containerApiMgr.searchForContentOfContainer(small.getId(), null, null, page, user)),
        collectionFetches(
            () ->
                containerApiMgr.searchForContentOfContainer(
                    large.getId(), null, null, page, user)));
  }

  @Test
  void containerPageHydratesOnlyReturnedOccupants() {
    ApiContainer container = createContainerWithIndependentSamples(8);
    var page = PaginationCriteria.createDefaultForClass(InventoryRecord.class);
    page.setResultsPerPage(1);
    flushDatabaseState();
    clearSessionAndEvictAll();
    Statistics statistics = sessionFactory.getStatistics();
    boolean enabled = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(true);
    try {
      var subsamples = statistics.getEntityStatistics(SubSample.class.getName());
      long before = subsamples.getLoadCount();
      long queriesBefore = occupantQueryExecutions(statistics);
      var result =
          containerApiMgr.searchForContentOfContainer(container.getId(), null, null, page, user);
      assertEquals(8L, result.getTotalHits());
      assertEquals(1, result.getRecords().size());
      assertEquals(1L, subsamples.getLoadCount() - before);
      assertEquals(1L, occupantQueryExecutions(statistics) - queriesBefore);
    } finally {
      statistics.setStatisticsEnabled(enabled);
    }
  }

  private long occupantQueryExecutions(Statistics statistics) {
    return Arrays.stream(statistics.getQueries())
        .filter(query -> query.contains("from ContainerLocation location"))
        .mapToLong(query -> statistics.getQueryStatistics(query).getExecutionCount())
        .sum();
  }

  @Test
  void containerPageKeepsLexicalOrderAndLocationTies() {
    ApiContainer container = createBasicContainerForUser(user);
    var first = addChild(container, "Tube.9", "First sample");
    var second = addChild(container, "Tube.10", "Second sample");
    var third = addChild(container, "tube.9", "Third sample");
    moveContainerIntoListContainer(
        createBasicContainerForUser(user).getId(), container.getId(), user);
    flushDatabaseState();
    clearSessionAndEvictAll();

    var page = PaginationCriteria.createDefaultForClass(InventoryRecord.class);
    page.setOrderBy(SearchUtils.ORDER_BY_NAME);
    page.setSortOrder(SortOrder.ASC);
    page.setPageNumber(99L);
    var result =
        containerApiMgr.searchForContentOfContainer(
            container.getId(), user.getUsername(), InventorySearchType.SUBSAMPLE, page, user);
    assertEquals(3L, result.getTotalHits());
    assertEquals(99, result.getPageNumber());
    assertEquals(
        List.of(second, first, third), result.getRecords().stream().map(r -> r.getId()).toList());

    page.setSortOrder(SortOrder.DESC);
    result =
        containerApiMgr.searchForContentOfContainer(
            container.getId(), null, InventorySearchType.SUBSAMPLE, page, user);
    assertEquals(
        List.of(third, first, second), result.getRecords().stream().map(r -> r.getId()).toList());
    assertEquals(
        0L,
        containerApiMgr
            .searchForContentOfContainer(
                container.getId(), user.getUsername().toUpperCase(Locale.ROOT), null, page, user)
            .getTotalHits());
  }

  private Long addChild(ApiContainer container, String childName, String sampleName) {
    ApiSubSample child = new ApiSubSample(childName);
    child.setParentContainer(container);
    return createBasicSampleWithSubsampleForUser(child, sampleName, null, user)
        .getSubSamples()
        .get(0)
        .getId();
  }

  @Test
  void movingOneRecordDoesNotFetchMoreMetadataForLargerContainers() {
    ApiContainer small = createContainerWithIndependentSamples(2);
    ApiContainer large = createContainerWithIndependentSamples(8);
    ApiContainer destination = createBasicContainerForUser(user);
    ApiSubSample smallMove = subsampleMove(small, destination);
    ApiSubSample largeMove = subsampleMove(large, destination);

    assertEquals(
        collectionFetches(() -> move(smallMove)), collectionFetches(() -> move(largeMove)));
  }

  @Test
  void fetchingOneChildDoesNotFetchMoreCollectionsForMoreSiblings() {
    Long smallChild = createSampleWithChildren(2);
    Long largeChild = createSampleWithChildren(8);

    assertEquals(
        collectionFetches(() -> subSampleApiMgr.getApiSubSampleById(smallChild, user)),
        collectionFetches(() -> subSampleApiMgr.getApiSubSampleById(largeChild, user)));
  }

  @Test
  void storesTheActiveChildCountAcrossCreationDeletionAndRestoration() {
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    Long sampleId = sample.getId();
    assertStoredCount(sampleId, 1);

    Long addedChild =
        subSampleApiMgr.createNewSubSamplesForSample(sampleId, 1, null, user).get(0).getId();
    assertStoredCount(sampleId, 2);

    subSampleApiMgr.markSubSampleAsDeleted(addedChild, user, false);
    assertStoredCount(sampleId, 1);

    subSampleApiMgr.restoreDeletedSubSample(addedChild, user, false);
    assertStoredCount(sampleId, 2);

    sampleApiMgr.markSampleAsDeleted(sampleId, true, user);
    assertStoredCount(sampleId, 2);

    sampleApiMgr.restoreDeletedSample(sampleId, user, true);
    assertStoredCount(sampleId, 2);
  }

  private void assertStoredCount(Long sampleId, int count) {
    flushDatabaseState();
    clearSessionAndEvictAll();
    Integer storedCount =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select activeSubSamplesCount from SampleEntity where id = :id", Integer.class)
            .setParameter("id", sampleId)
            .getSingleResult();
    assertEquals(count, storedCount);
  }

  private ApiContainer createContainerWithIndependentSamples(int count) {
    ApiContainer container = createBasicContainerForUser(user, "Container with " + count);
    for (int i = 0; i < count; i++) {
      ApiSubSample child = new ApiSubSample("Child " + i);
      child.setParentContainer(container);
      createBasicSampleWithSubsampleForUser(child, "Parent " + i, null, user);
    }
    return containerApiMgr.getApiContainerById(container.getId(), user);
  }

  private Long createSampleWithChildren(int count) {
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples("Sample with " + count);
    sample.setSubSamples(
        IntStream.range(0, count).mapToObj(i -> new ApiSubSample("Child " + i)).toList());
    return sampleApiMgr.createNewApiSample(sample, user).getSubSamples().get(0).getId();
  }

  private ApiSubSample subsampleMove(ApiContainer source, ApiContainer destination) {
    ApiSubSample move = new ApiSubSample();
    move.setId(source.getLocations().get(0).getContent().getId());
    move.setParentContainer(destination);
    return move;
  }

  private void move(ApiSubSample move) {
    var result =
        inventoryBulkOpApiMgr.runBulkOperation(
            new InventoryBulkOperationConfig(BulkApiOperationType.MOVE, List.of(move), true, user));
    assertEquals(1, result.getSuccessCount());
  }

  private Map<String, Long> collectionFetches(Runnable operation) {
    flushDatabaseState();
    clearSessionAndEvictAll();
    Statistics statistics = sessionFactory.getStatistics();
    boolean enabled = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(true);
    try {
      Map<String, Long> fetches = new LinkedHashMap<>();
      for (Class<?> owner : List.of(SampleEntity.class, SubSample.class)) {
        for (String property : List.of("extraFields", "barcodes", "identifiers", "files")) {
          fetches.put(owner.getName() + "." + property, 0L);
        }
      }
      fetches.put(SampleEntity.class.getName() + ".subSamples", 0L);
      fetches.replaceAll(
          (role, ignored) -> statistics.getCollectionStatistics(role).getFetchCount());
      operation.run();
      fetches.replaceAll(
          (role, before) -> statistics.getCollectionStatistics(role).getFetchCount() - before);
      return fetches;
    } finally {
      statistics.setStatisticsEnabled(enabled);
    }
  }
}
