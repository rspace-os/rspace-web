package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;

import com.researchspace.core.util.IPagination;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.views.FormSearchCriteria;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FormPaginateURLTest {
  private FormPaginatedURLGenerator gen;

  @Before
  public void setUp() throws Exception {
    gen = new FormPaginatedURLGenerator(null, null);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGenerateURL() {
    FormSearchCriteria tsc = new FormSearchCriteria();
    PaginationCriteria<RSForm> pc = new PaginationCriteria<RSForm>(RSForm.class);
    tsc.setSearchTerm(" with spaces");
    gen = new FormPaginatedURLGenerator(tsc, pc);
    assertEquals(
        "/workspace/editor/form/ajax/list?searchTerm=%20with%20spaces&pageNumber=0"
            + "&resultsPerPage="
            + IPagination.DEFAULT_RESULTS_PERPAGE
            + "&sortOrder=DESC",
        gen.generateURL(0));
  }
}
