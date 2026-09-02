package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.dtos.RevisionSearchCriteria;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

public class RevisionSearchCriteriaTest {

  RevisionSearchCriteria ar = new RevisionSearchCriteria();

  @Test
  public void testGetSearchTermField2Values()
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    assertEquals(0, ar.getSearchTermField2Values().keySet().size());
    ar.setModifiedBy("user");
    assertEquals(1, ar.getSearchTermField2Values().keySet().size());
    ar.setSelectedFields(new String[] {"a"});
    assertEquals(2, ar.getSearchTermField2Values().keySet().size());
    // empty array should npt be added to map
    ar.setSelectedFields(new String[] {});
    assertEquals(1, ar.getSearchTermField2Values().keySet().size());
  }
}
