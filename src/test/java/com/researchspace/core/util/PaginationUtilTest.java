package com.researchspace.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class PaginationUtilTest {

  @Test
  public void testGenerateRecordsPerPAgeLinks() {
    IPagination<Object> pgCrit = BasicPaginationCriteria.createDefaultForClass(Object.class);
    DefaultURLPaginator pg = new DefaultURLPaginator("/a", pgCrit);
    List<PaginationObject> pos = PaginationUtil.generateRecordsPerPageListing(pg, "any");
    assertThat(pos).hasSize(4);
  }

  @Test
  public void testOrderByLinks() {
    IPagination<Object> pgCrit = BasicPaginationCriteria.createDefaultForClass(Object.class);
    DefaultURLPaginator urlGenerator = new DefaultURLPaginator("/a", pgCrit);
    Map<String, PaginationObject> rc =
        PaginationUtil.generateOrderByLinks(null, urlGenerator, "name");
    assertNotNull(rc);
    assertEquals("orderByNameLink", rc.keySet().iterator().next());
    assertNotNull(rc.get("orderByNameLink"));
    assertThat(rc.get("orderByNameLink").getLink()).contains("name");
    // if no properties a re passed in, thi sis handled gracefully
    Map<String, PaginationObject> rc2 =
        PaginationUtil.generateOrderByLinks(null, urlGenerator, null);
    assertThat(rc2).isEmpty();
  }

  @Test
  public void orderByLinksRestoreOriginalOrderBy() {
    IPagination<Object> unsorted = BasicPaginationCriteria.createDefaultForClass(Object.class);
    PaginationUtil.generateOrderByLinks(null, new DefaultURLPaginator("/a", unsorted), "name");
    assertNull(unsorted.getOrderBy());

    IPagination<Object> sorted = BasicPaginationCriteria.createDefaultForClass(Object.class);
    sorted.setOrderBy("creationDate");
    PaginationUtil.generateOrderByLinks(null, new DefaultURLPaginator("/a", sorted), "name");
    assertEquals("creationDate", sorted.getOrderBy());
  }
}
