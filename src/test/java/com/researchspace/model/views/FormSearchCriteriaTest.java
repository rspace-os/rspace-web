package com.researchspace.model.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FormSearchCriteriaTest {

  private FormSearchCriteria sc;

  @BeforeEach
  public void setUp() throws Exception {
    sc = new FormSearchCriteria();
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testCanSetValidSearchTerm() {
    sc.setSearchTerm(null);
    sc.setSearchTerm("");
    sc.setSearchTerm(" a valid name");
    sc.setSearchTerm(" user's name");
  }

  @Test
  public void testSearchFieldC()
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    assertEquals(2, sc.getSearchTermField2Values().keySet().size());
    sc.setSearchTerm("anyname");
    assertEquals(3, sc.getSearchTermField2Values().keySet().size());
    assertFalse(sc.getURLQueryString().endsWith("&"));
    assertTrue(sc.getURLQueryString().contains("anyname"));
  }
}
