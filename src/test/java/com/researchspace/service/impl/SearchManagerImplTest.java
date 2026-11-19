package com.researchspace.service.impl;

import static com.researchspace.Constants.SYSADMIN_ROLE;
import static com.researchspace.testutils.SearchTestUtils.createSimpleFullTextSearchCfg;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axiope.search.InventorySearchConfig;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.TextSearchDao;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchManagerImplTest {

  @Mock IPropertyHolder properties;
  @Mock MessageSourceUtils messages;
  @Mock TextSearchDao textSearchDao;
  @Mock SampleApiManager sampleApiManager;
  @InjectMocks SearchManagerImpl searchManager;

  @Test
  void sysadminFloorComesFromTheDeploymentProperties() {
    when(properties.getSysadminMinSearchTermLength()).thenReturn(10);
    User sysadmin = TestFactory.createAnyUserWithRole("sys", SYSADMIN_ROLE);
    WorkspaceListingConfig sevenChars = createSimpleFullTextSearchCfg("abcdefg");

    assertThrows(
        IllegalArgumentException.class,
        () -> searchManager.searchWorkspaceRecords(sevenChars, sysadmin));
    verifyNoInteractions(textSearchDao);
  }

  @Test
  void userFloorComesFromTheDeploymentProperties() {
    when(properties.getUserMinSearchTermLength()).thenReturn(4);
    User user = TestFactory.createAnyUser("any");
    WorkspaceListingConfig threeChars = createSimpleFullTextSearchCfg("abc");

    assertThrows(
        IllegalArgumentException.class,
        () -> searchManager.searchWorkspaceRecords(threeChars, user));
    verifyNoInteractions(textSearchDao);
  }

  @Test
  void inventorySearchTakesTheSysadminFloorFromTheDeploymentProperties() {
    when(properties.getSysadminMinSearchTermLength()).thenReturn(10);
    User sysadmin = TestFactory.createAnyUserWithRole("sys", SYSADMIN_ROLE);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            searchManager.searchInventoryWithSimpleQuery(
                "abcdefg", null, null, null, null, null, sysadmin));
    verifyNoInteractions(textSearchDao);
  }

  /** Lucene reports that it hit its result limit, as FullTextSearcherImpl does. */
  private User sysadminWithLuceneLimitReached() {
    when(properties.getSysadminMinSearchTermLength()).thenReturn(1);
    doAnswer(
            invocation -> {
              invocation
                  .getArgument(0, InventorySearchConfig.class)
                  .setLuceneResultLimitReached(true);
              return new SearchResultsImpl<>(List.of(), 0, 0L);
            })
        .when(textSearchDao)
        .getSearchedInventoryResults(any());
    when(sampleApiManager.convertToApiInventorySearchResult(anyLong(), anyInt(), anyList(), any()))
        .thenAnswer(invocation -> new ApiInventorySearchResult());
    return TestFactory.createAnyUserWithRole("sys", SYSADMIN_ROLE);
  }

  @Test
  void flagsTextSearchLimitWhenResultsAreLimitedToStructureHits() {
    User sysadmin = sysadminWithLuceneLimitReached();

    ApiInventorySearchResult result =
        searchManager.searchInventoryWithSimpleQuery(
            "buffer", null, null, null, null, null, List.of("SA1"), null, sysadmin);

    assertTrue(result.getTextSearchLimitReached());
  }

  @Test
  void doesNotFlagTextSearchLimitForPlainTextSearch() {
    User sysadmin = sysadminWithLuceneLimitReached();

    ApiInventorySearchResult result =
        searchManager.searchInventoryWithSimpleQuery(
            "buffer", null, null, null, null, null, null, null, sysadmin);

    assertNull(result.getTextSearchLimitReached());
  }
}
