package com.researchspace.dao.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.axiope.search.SearchConstants;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RSForm;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NameDateFilterTest {

  class NameDateFilterTSS extends NameDateFilterImpl {
    // overrides  actual database lookup to return a template
    List<RSForm> searchDBForForms(String[] tms) {
      return Arrays.asList(new RSForm[] {TestFactory.createAnyForm("any")});
    }
  }

  NameDateFilterImpl ndf;

  @Before
  public void setUp() throws Exception {
    ndf = new NameDateFilterTSS();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGenerateQueryString() {
    List<String> pname = new ArrayList<String>();
    List<Object> pval = new ArrayList<Object>();
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    pgCrit.setOrderByIfNull("name");
    WorkspaceListingConfig input = createANameSearchInput(pgCrit);
    ndf.generateCountQueryString(input, pname, pval);
    assertEquals(pname.size(), pval.size());
    assertEquals(5, pname.size());

    pname.clear();
    pval.clear();
    String from2 = ndf.generateRetrieveQueryString(input, pname, pval);
    assertEquals(pname.size(), pval.size());
    assertEquals(5, pname.size());
    assertTrue(from2.contains(" order by name"));
  }

  private WorkspaceListingConfig createANameSearchInput(PaginationCriteria<BaseRecord> pgCrit) {
    return new WorkspaceListingConfig(
        pgCrit,
        new String[] {SearchConstants.NAME_SEARCH_OPTION},
        new String[] {"testname"},
        1L,
        false);
  }
}
