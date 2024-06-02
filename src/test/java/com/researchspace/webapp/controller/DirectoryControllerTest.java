package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;

import com.researchspace.model.dtos.UserSearchCriteria;
import org.junit.Before;
import org.junit.Test;

public class DirectoryControllerTest {
  DirectoryController controller;

  @Before
  public void setUp() {
    controller = new DirectoryController();
  }

  @Test
  public void testCleanSearchTerm() {
    UserSearchCriteria crit = new UserSearchCriteria();
    crit.setAllFields("%%%");
    controller.cleanSearchTerm(crit);
    assertEquals("", crit.getAllFields());

    crit.setAllFields("%A%");
    controller.cleanSearchTerm(crit);
    assertEquals("A", crit.getAllFields());

    crit.setAllFields("%A*?");
    controller.cleanSearchTerm(crit);
    assertEquals("A", crit.getAllFields());
  }
}
