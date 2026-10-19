package com.researchspace.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.model.dtos.RevisionSearchCriteria;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

public class RevisionSearchCriteriaTest {

  RevisionSearchCriteria ar = new RevisionSearchCriteria();

  @Test
  public void testGetSearchTermField2Values()
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    assertThat(ar.getSearchTermField2Values()).isEmpty();
    ar.setModifiedBy("user");
    assertThat(ar.getSearchTermField2Values().keySet()).hasSize(1);
    ar.setSelectedFields(new String[] {"a"});
    assertThat(ar.getSearchTermField2Values().keySet()).hasSize(2);
    // empty array should npt be added to map
    ar.setSelectedFields(new String[] {});
    assertThat(ar.getSearchTermField2Values().keySet()).hasSize(1);
  }
}
