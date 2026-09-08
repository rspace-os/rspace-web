package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.axiope.search.SearchConstants;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RSForm;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NameDateFilterTest {

  class NameDateFilterTSS extends NameDateFilterImpl {
    // overrides  actual database lookup to return a template
    List<RSForm> searchDBForForms(String[] tms) {
      return Arrays.asList(new RSForm[] {TestFactory.createAnyForm("any")});
    }
  }

  NameDateFilterImpl ndf;

  @BeforeEach
  public void setUp() throws Exception {
    ndf = new NameDateFilterTSS();
  }

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

  @Test
  public void unsafeOrderByUsesStableFallbackInGeneratedQuery() {
    List<String> pname = new ArrayList<String>();
    List<Object> pval = new ArrayList<Object>();
    // setOrderBy drops unsafe values, so a mock simulates a criteria object that
    // still carries an ORDER BY injection payload and pins the query-builder guard
    @SuppressWarnings("unchecked")
    PaginationCriteria<BaseRecord> pgCrit = mock(PaginationCriteria.class);
    when(pgCrit.getOrderBy()).thenReturn("name,rand()");
    when(pgCrit.isOrderBySafe("name,rand()")).thenReturn(false);
    WorkspaceListingConfig input = createANameSearchInput(pgCrit);

    String countQuery = ndf.generateCountQueryString(input, pname, pval);
    pname.clear();
    pval.clear();
    String retrieveQuery = ndf.generateRetrieveQueryString(input, pname, pval);
    pname.clear();
    pval.clear();
    String formQuery = ndf.generateFilterStrucDocByFormQuery(input, pname, pval, Arrays.asList(1L));

    assertFalse(countQuery.toLowerCase().contains("order by"));
    assertFalse(countQuery.contains("rand()"));
    assertTrue(retrieveQuery.contains("order by rc.id ASC"));
    assertFalse(retrieveQuery.contains("rand()"));
    assertTrue(formQuery.contains("order by r.id ASC"));
    assertFalse(formQuery.contains("rand()"));
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
