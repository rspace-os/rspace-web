package com.researchspace.model.dtos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.axiope.search.SearchConstants;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.record.BaseRecord;
import java.net.URISyntaxException;
import java.text.ParseException;
import org.junit.Test;
import org.springframework.test.util.JsonPathExpectationsHelper;

public class WorkspaceSettingsTest {

  @Test
  public void toJsonRoundTripTest() throws URISyntaxException, ParseException {
    WorkspaceSettings settings = new WorkspaceSettings();
    settings.setFavoritesFilter(true);
    settings.setPageNumber(2L);
    String[] opts = new String[] {"a", "b", "c", "d", "e"};
    String[] terms = new String[] {"1", "2", "3", "4", "5"};
    String json = settings.toJson(opts, terms);
    // check search options added OK
    JsonPathExpectationsHelper jsonAssert = new JsonPathExpectationsHelper("$.options[0]");
    jsonAssert.assertValue(json, "a");
    jsonAssert = new JsonPathExpectationsHelper("$.terms[2]");
    jsonAssert.assertValue(json, "3");

    WorkspaceSettings settings2 = JacksonUtil.fromJson(json, WorkspaceSettings.class);
    assertEquals(settings, settings2);
  }

  @Test
  public void testGenerateSearchInput() {
    GalleryFilterCriteria filter = new GalleryFilterCriteria();
    PaginationCriteria<BaseRecord> pgcrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);

    filter.setName("any");
    WorkspaceListingConfig searchInput = new WorkspaceListingConfig(pgcrit, 1L, filter);
    assertNotNull("valid search terms shouldn't produce null search input", searchInput);
    assertEquals(
        "only search by name is possible just now",
        SearchConstants.NAME_SEARCH_OPTION,
        searchInput.getSrchOptions()[0]);
  }
}
