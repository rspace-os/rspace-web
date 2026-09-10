package com.researchspace.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.dao.SampleTemplateDao;
import com.researchspace.model.User;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

public class InventorySearchApiControllerTest extends SpringTransactionalTest {

  private @Autowired SamplesApiController samplesApi;
  private @Autowired ContainersApiController containersApi;
  private @Autowired InventorySearchApiController searchApi;
  private @Autowired SampleTemplateDao sampleTemplateDao;

  private BindingResult mockBindingResult = mock(BindingResult.class);
  private User testUser;

  @BeforeEach
  public void setUp() {
    sampleTemplateDao.resetDefaultTemplateOwner();

    testUser = createInitAndLoginAnyUser();
    assertTrue(testUser.isContentInitialized());
    when(mockBindingResult.hasErrors()).thenReturn(false);
  }

  @Test
  public void retrievePaginatedSampleList() throws BindException {

    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("aaa sample");
    newSample.setNewSampleSubSamplesCount(2);
    ApiSampleWithFullSubSamples createdSample =
        samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    assertNotNull(createdSample);
    String aaaSampleGlobalId = createdSample.getGlobalId();

    List<ApiInventoryRecordInfo> xyzSamples =
        createMultipleSamplesForUser("sample XYZ aaa", 2, testUser);
    assertThat(xyzSamples).hasSize(2);

    ApiContainer newContainer = new ApiContainer();
    newContainer.setName("zzz aaa sample container");
    containersApi.createNewContainer(newContainer, mockBindingResult, testUser);

    flushToSearchIndices();

    InventoryApiSearchConfig searchConfig = new InventoryApiSearchConfig();
    final int EXPECTED_TOTAL_HITS = 8; // includes sample templates
    // no pagination parameters, term matching all samples, subsamples and container
    searchConfig.setQuery("aaa");
    ApiInventorySearchResult searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, testUser);
    assertEquals(EXPECTED_TOTAL_HITS, searchResult.getTotalHits().intValue());
    assertThat(searchResult.getRecords())
        .hasSize(EXPECTED_TOTAL_HITS); // sample & subsamples & container
    assertEquals(
        "aaa sample", searchResult.getRecords().get(0).getName()); // default ordering is name asc
    assertEquals("aaa sample.01", searchResult.getRecords().get(1).getName());
    assertEquals("aaa sample.02", searchResult.getRecords().get(2).getName());
    assertEquals("sample XYZ aaa-01", searchResult.getRecords().get(3).getName());
    assertEquals("zzz aaa sample container", searchResult.getRecords().get(7).getName());
    assertThat(searchResult.getLinks()).hasSize(1);

    // name desc ordering, 2-rec-per-page pagination
    InventoryApiPaginationCriteria pgCrit = new InventoryApiPaginationCriteria(0, 2, "name desc");
    searchResult =
        searchApi.searchInventoryRecords(pgCrit, searchConfig, mockBindingResult, testUser);
    assertEquals(EXPECTED_TOTAL_HITS, searchResult.getTotalHits().intValue());
    assertThat(searchResult.getRecords()).hasSize(2); // subsample & sample
    assertEquals("zzz aaa sample container", searchResult.getRecords().get(0).getName());
    assertEquals("sample XYZ aaa-02.01", searchResult.getRecords().get(1).getName());
    assertThat(searchResult.getLinks()).hasSize(3);

    // type desc ordering, 10-rec-per-page pagination
    pgCrit = new InventoryApiPaginationCriteria(0, 10, "type desc");
    searchResult =
        searchApi.searchInventoryRecords(pgCrit, searchConfig, mockBindingResult, testUser);
    assertEquals(EXPECTED_TOTAL_HITS, searchResult.getTotalHits().intValue());
    assertThat(searchResult.getRecords().get(0).getGlobalId()).startsWith("SS"); // subsamples first
    assertThat(searchResult.getRecords().get(1).getGlobalId()).startsWith("SS");
    assertThat(searchResult.getRecords().get(2).getGlobalId()).startsWith("SS");
    assertThat(searchResult.getRecords().get(3).getGlobalId()).startsWith("SS");
    assertThat(searchResult.getRecords().get(4).getGlobalId()).startsWith("SA"); // then samples
    assertThat(searchResult.getRecords().get(5).getGlobalId()).startsWith("SA");
    assertThat(searchResult.getRecords().get(6).getGlobalId()).startsWith("SA");
    assertThat(searchResult.getRecords().get(7).getGlobalId()).startsWith("IC"); // then containers
    assertThat(searchResult.getLinks()).hasSize(1);

    // global id asc ordering, 10-rec-per-page pagination
    pgCrit = new InventoryApiPaginationCriteria(0, 10, "globalId asc");
    searchResult =
        searchApi.searchInventoryRecords(pgCrit, searchConfig, mockBindingResult, testUser);
    assertEquals(EXPECTED_TOTAL_HITS, searchResult.getTotalHits().intValue());
    assertEquals(
        "zzz aaa sample container", searchResult.getRecords().get(0).getName()); // containers first
    assertEquals("aaa sample", searchResult.getRecords().get(1).getName()); // then samples, by id
    assertEquals("sample XYZ aaa-01", searchResult.getRecords().get(2).getName());
    assertEquals("sample XYZ aaa-02", searchResult.getRecords().get(3).getName());
    assertEquals(
        "aaa sample.01", searchResult.getRecords().get(4).getName()); // then subsamples, by id
    assertEquals("aaa sample.02", searchResult.getRecords().get(5).getName());
    assertEquals("sample XYZ aaa-01.01", searchResult.getRecords().get(6).getName());
    assertEquals("sample XYZ aaa-02.01", searchResult.getRecords().get(7).getName());

    // creation date desc ordering, 10-rec-per-page pagination
    pgCrit = new InventoryApiPaginationCriteria(0, 10, "creationDate desc");
    searchResult =
        searchApi.searchInventoryRecords(pgCrit, searchConfig, mockBindingResult, testUser);
    assertEquals(EXPECTED_TOTAL_HITS, searchResult.getTotalHits().intValue());
    assertEquals(
        "zzz aaa sample container", searchResult.getRecords().get(0).getName()); // containers first
    assertThat(searchResult.getRecords().get(1).getName()).startsWith("sample XYZ aaa-02");
    assertThat(searchResult.getRecords().get(7).getName()).startsWith("aaa sample");

    // modification date asc ordering, 10-rec-per-page pagination
    pgCrit = new InventoryApiPaginationCriteria(0, 10, "modificationDate asc");
    searchResult =
        searchApi.searchInventoryRecords(pgCrit, searchConfig, mockBindingResult, testUser);
    assertEquals(EXPECTED_TOTAL_HITS, searchResult.getTotalHits().intValue());
    assertThat(searchResult.getRecords().get(0).getName()).startsWith("aaa sample");
    assertThat(searchResult.getRecords().get(5).getName()).startsWith("sample XYZ aaa-02");
    assertThat(searchResult.getRecords().get(6).getName()).startsWith("sample XYZ aaa-02");
    assertEquals("zzz aaa sample container", searchResult.getRecords().get(7).getName());

    // search within sample (by parentGlobalId)
    searchConfig = new InventoryApiSearchConfig();
    searchConfig.setParentGlobalId(aaaSampleGlobalId);
    searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, testUser);
    assertThat(searchResult.getRecords()).hasSize(2);
    assertEquals("aaa sample.01", searchResult.getRecords().get(0).getName());
    String selfLink = searchResult.getLinks().get(0).getLink();
    assertThat(selfLink).as("no parentGlobalId in link: " + selfLink).contains("parentGlobalId=");

    // search within a sample with a query matching just one of the subsamples
    searchConfig.setQuery("02");
    searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, testUser);
    assertThat(searchResult.getRecords()).hasSize(1);
    assertEquals("aaa sample.02", searchResult.getRecords().get(0).getName());
  }

  @Test
  public void searchContainerContent() throws BindException {
    User exampleContentUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(exampleContentUser);
    logoutAndLoginAs(exampleContentUser);

    flushToSearchIndices();

    // find default subcontainer by name
    InventoryApiSearchConfig searchConfig = new InventoryApiSearchConfig();
    searchConfig.setQuery("box #1 (list container)");
    searchConfig.setResultType("CONTAINER");
    ApiInventorySearchResult searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, exampleContentUser);
    assertThat(searchResult.getRecords()).hasSize(1);

    ApiInventoryRecordInfo foundSubcontainer = searchResult.getRecords().get(0);
    assertEquals("box #1 (list container)", foundSubcontainer.getName());
    String subcontainerId = foundSubcontainer.getGlobalId();

    // search for the content of default subcontainer, with pagination
    searchConfig = new InventoryApiSearchConfig();
    searchConfig.setParentGlobalId(subcontainerId);
    InventoryApiPaginationCriteria pgCrit = new InventoryApiPaginationCriteria(0, 2, "name desc");
    searchResult =
        searchApi.searchInventoryRecords(
            pgCrit, searchConfig, mockBindingResult, exampleContentUser);
    assertThat(searchResult.getRecords()).hasSize(2);
    assertEquals("box C (list container)", searchResult.getRecords().get(0).getName());
    List<ApiLinkItem> searchResultLinks = searchResult.getLinks();
    assertThat(searchResultLinks).hasSize(2);
    String selfLink = searchResultLinks.get(0).getLink();
    assertThat(selfLink)
        .as("no parentGlobalId in link: " + selfLink)
        .contains("parentGlobalId=" + subcontainerId);
    // check 2nd page of pagination
    pgCrit = new InventoryApiPaginationCriteria(1, 2, "name desc");
    searchResult =
        searchApi.searchInventoryRecords(
            pgCrit, searchConfig, mockBindingResult, exampleContentUser);
    assertThat(searchResult.getRecords()).hasSize(2);
    assertEquals("box A (list container)", searchResult.getRecords().get(0).getName());
    assertEquals("Basic Sample.01", searchResult.getRecords().get(1).getName());

    // search within container, but with excluding deleted items
    searchConfig.setDeletedItems(InventorySearchDeletedOption.DELETED_ONLY.toString());
    searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, exampleContentUser);
    assertThat(searchResult.getRecords())
        .isEmpty(); // no results as deleted is outside of any container

    // search with term and within the default subcontainer
    searchConfig.setDeletedItems(InventorySearchDeletedOption.EXCLUDE.toString());
    searchConfig.setQuery("basic");
    searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, exampleContentUser);
    assertThat(searchResult.getRecords()).hasSize(1);
    assertEquals("Basic Sample.01", searchResult.getRecords().get(0).getName());
    // subsample listed in search results should contain details of its parent (RSINV-70)
    assertNotNull(searchResult.getRecords().get(0).getParentContainer());
    assertEquals(
        "box #1 (list container)", searchResult.getRecords().get(0).getParentContainer().getName());

    // search without the term but limited to containers within default subcontainer
    searchConfig.setQuery("");
    searchConfig.setResultType("CONTAINER");
    searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, exampleContentUser);
    assertThat(searchResult.getRecords()).hasSize(3);
    assertEquals("box A (list container)", searchResult.getRecords().get(0).getName());
    selfLink = searchResult.getLinks().get(0).getLink();
    assertThat(selfLink).as("no resultType in link: " + selfLink).contains("resultType=CONTAINER");
    // container listed in search results should contain details of its parent (RSINV-70)
    assertNotNull(searchResult.getRecords().get(0).getParentContainer());
    assertEquals(
        "box #1 (list container)", searchResult.getRecords().get(0).getParentContainer().getName());

    // find image container
    searchConfig.setParentGlobalId(null);
    searchConfig.setQuery("image");
    searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, exampleContentUser);
    assertThat(searchResult.getRecords()).hasSize(1);
    ApiInventoryRecordInfo foundImageContainerInfo = searchResult.getRecords().get(0);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_NAME,
        foundImageContainerInfo.getName());
    assertThat(foundImageContainerInfo.getLinks())
        .as("expected self + 3 image links to be present")
        .hasSize(4);
    ApiLinkItem containerLink = foundImageContainerInfo.getLinks().get(2);
    assertEquals(ApiLinkItem.SELF_REL, containerLink.getRel());
    assertThat(containerLink.getLink())
        .as("unexpected self link: " + containerLink.getLink())
        .endsWith("/api/inventory/v1/containers/" + foundImageContainerInfo.getId());
  }

  @Test
  public void retrieveSampleSubSamples() throws BindException {

    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("aaa sample");
    newSample.setNewSampleSubSamplesCount(5);
    ApiSampleWithFullSubSamples createdSample =
        samplesApi.createNewSample(newSample, mockBindingResult, testUser);
    assertNotNull(createdSample);
    String aaaSampleGlobalId = createdSample.getGlobalId();

    flushToSearchIndices();

    // find all subsamples belonging to a sample
    InventoryApiSearchConfig searchConfig = new InventoryApiSearchConfig();
    searchConfig.setParentGlobalId(aaaSampleGlobalId);
    ApiInventorySearchResult searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, testUser);
    assertThat(searchResult.getRecords()).hasSize(5);
    ApiInventoryRecordInfo firstSubSample = searchResult.getRecords().get(0);
    assertEquals("aaa sample.01", firstSubSample.getName());
    assertFalse(firstSubSample.isDeleted());

    // delete one of the subsamples
    ApiSubSample deletedSubSample =
        subSampleApiMgr.markSubSampleAsDeleted(firstSubSample.getId(), testUser, false);
    assertTrue(deletedSubSample.isDeleted());

    // run the query again - deleted subsamples shouldn't be listed
    searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, testUser);
    assertThat(searchResult.getRecords()).hasSize(4);

    // ... unless search config asks to include deleted
    searchConfig.setDeletedItems(InventorySearchDeletedOption.INCLUDE.toString());
    searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, testUser);
    assertThat(searchResult.getRecords()).hasSize(5);

    // ... or if asks just for deleted
    searchConfig.setDeletedItems(InventorySearchDeletedOption.DELETED_ONLY.toString());
    searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, testUser);
    assertThat(searchResult.getRecords()).hasSize(1);
    assertEquals(firstSubSample.getId(), searchResult.getRecords().get(0).getId());

    // search within a sample, but only report results of SAMPLE type (there won't be any)
    searchConfig.setResultType("SAMPLE");
    searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, testUser);
    assertThat(searchResult.getRecords()).isEmpty();
  }

  @Test
  public void searchSamplesCreatedFromTemplate() throws BindException, IOException {
    createBasicSampleForUser(testUser);

    ApiSampleWithFullSubSamples complexSample1 = createComplexSampleForUser(testUser);
    complexSample1.setName("complex first");
    sampleApiMgr.updateApiSample(complexSample1, testUser);

    ApiSampleWithFullSubSamples complexSample2 = createComplexSampleForUser(testUser);
    complexSample2.setName("complex second");
    sampleApiMgr.updateApiSample(complexSample2, testUser);

    flushToSearchIndices();

    // find all samples (empty query string) created from complex template
    InventoryApiSearchConfig searchConfig = new InventoryApiSearchConfig();
    searchConfig.setQuery("");
    searchConfig.setParentGlobalId("IT" + complexSample1.getTemplateId());
    ApiInventorySearchResult searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, testUser);
    assertThat(searchResult.getRecords()).hasSize(2);

    // find a sample created from complex template, with a query string
    searchConfig.setQuery("second");
    searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, testUser);
    assertThat(searchResult.getRecords()).hasSize(1);
  }

  @Test
  public void workbenchesNotReturnedBySearch() throws BindException, IOException {
    ApiContainer workbench = getWorkbenchForUser(testUser);

    flushToSearchIndices();

    InventoryApiSearchConfig searchConfig = new InventoryApiSearchConfig();
    searchConfig.setQuery(workbench.getName());
    ApiInventorySearchResult searchResult =
        searchApi.searchInventoryRecords(null, searchConfig, mockBindingResult, testUser);
    assertThat(searchResult.getRecords()).isEmpty();
  }
}
