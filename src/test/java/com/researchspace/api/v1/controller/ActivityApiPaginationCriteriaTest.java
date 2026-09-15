package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.ActivityApiPaginationCriteria.DATE_ASC_API_PARAM;
import static com.researchspace.api.v1.controller.ActivityApiPaginationCriteria.DATE_DESC_API_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiSortEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;

public class ActivityApiPaginationCriteriaTest {

  ActivityApiPaginationCriteria eventPgCrit;

  @BeforeEach
  public void setUp() throws Exception {
    eventPgCrit = new ActivityApiPaginationCriteria();
  }

  @Test
  public void testGetDefault() {
    assertEquals(DATE_DESC_API_PARAM, eventPgCrit.getDefaultOrderBy());
    assertEquals(DATE_DESC_API_PARAM, eventPgCrit.getOrderBy());
  }

  @Test
  public void testValidSortOrders() {
    eventPgCrit.setOrderBy(DATE_DESC_API_PARAM);
    assertEquals(ApiSortEnum.DATE_DESC, eventPgCrit.getSort());
    eventPgCrit.setOrderBy(DATE_ASC_API_PARAM);
    assertEquals(ApiSortEnum.DATE_ASC, eventPgCrit.getSort());
  }

  @Test
  public void testInvalidSortOrders() {
    eventPgCrit.setOrderBy("created desc");
    assertThrows(IllegalArgumentException.class, () -> eventPgCrit.getSort());
  }

  @Test
  public void testToMap() {
    eventPgCrit = new ActivityApiPaginationCriteria(1, 10, DATE_ASC_API_PARAM);
    MultiValueMap<String, String> map = eventPgCrit.toMap();
    assertTrue(map.containsKey("pageNumber"));
    assertTrue(map.containsKey("pageSize"));
    assertTrue(map.containsKey("orderBy"));
  }
}
