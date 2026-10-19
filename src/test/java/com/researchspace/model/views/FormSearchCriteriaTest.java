package com.researchspace.model.views;

import static org.assertj.core.api.Assertions.assertThat;

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
    assertThat(sc.getSearchTermField2Values().keySet()).hasSize(2);
    sc.setSearchTerm("anyname");
    assertThat(sc.getSearchTermField2Values().keySet()).hasSize(3);
    assertThat(sc.getURLQueryString()).doesNotEndWith("&");
    assertThat(sc.getURLQueryString()).contains("anyname");
  }
}
