package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axiope.search.SearchConstants;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.sort.RecordSort;
import com.researchspace.model.sort.UnknownSortKeyException;
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
  public void unknownSortKeyIsRejectedBeforeAnyQueryIsBuilt() {
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    pgCrit.setOrderBy("name,rand()");
    WorkspaceListingConfig input = createANameSearchInput(pgCrit);
    assertThrows(
        UnknownSortKeyException.class,
        () -> ndf.generateRetrieveQueryString(input, new ArrayList<>(), new ArrayList<>()));
    assertThrows(
        UnknownSortKeyException.class,
        () ->
            ndf.generateFilterStrucDocByFormQuery(
                input, new ArrayList<>(), new ArrayList<>(), Arrays.asList(1L)));
  }

  @Test
  public void sortKeyWithoutAColumnInTheseQueriesUsesStableFallback() {
    List<String> pname = new ArrayList<String>();
    List<Object> pval = new ArrayList<Object>();
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    pgCrit.setOrderBy(RecordSort.TEMPLATE.key());
    WorkspaceListingConfig input = createANameSearchInput(pgCrit);

    String countQuery = ndf.generateCountQueryString(input, pname, pval);
    pname.clear();
    pval.clear();
    String retrieveQuery = ndf.generateRetrieveQueryString(input, pname, pval);
    pname.clear();
    pval.clear();
    String formQuery = ndf.generateFilterStrucDocByFormQuery(input, pname, pval, Arrays.asList(1L));

    assertFalse(countQuery.toLowerCase().contains("order by"));
    assertTrue(retrieveQuery.contains("order by rc.id ASC"));
    assertTrue(formQuery.contains("order by r.id ASC"));
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
