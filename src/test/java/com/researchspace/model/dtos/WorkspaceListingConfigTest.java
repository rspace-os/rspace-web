package com.researchspace.model.dtos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.dtos.WorkspaceSettings.WorkspaceViewMode;
import org.junit.jupiter.api.Test;

class WorkspaceListingConfigTest {

  @Test
  void keepSearchFromTakesOnlyThePreviousSearch() {
    WorkspaceListingConfig previous = new WorkspaceListingConfig();
    previous.setSrchOptions(new String[] {"name", "tag"});
    previous.setSrchTerms(new String[] {"Zebrafish", "bkmktag"});
    previous.setAdvancedSearch(true);
    previous.setOperator(SearchOperator.OR);
    previous.setParentFolderId(1L);
    WorkspaceListingConfig rejected = new WorkspaceListingConfig();
    rejected.setSrchOptions(new String[] {"global"});
    rejected.setSrchTerms(new String[] {"*abc"});
    rejected.setParentFolderId(2L);
    rejected.setCurrentViewMode(WorkspaceViewMode.TREE_VIEW);

    rejected.keepSearchFrom(previous);

    assertThat(rejected.getSrchOptions()).containsExactly("name", "tag");
    assertThat(rejected.getSrchTerms()).containsExactly("Zebrafish", "bkmktag");
    assertTrue(rejected.isAdvancedSearch());
    assertEquals(SearchOperator.OR, rejected.getOperator());
    assertEquals(2L, rejected.getParentFolderId());
    assertEquals(WorkspaceViewMode.TREE_VIEW, rejected.getCurrentViewMode());
  }
}
