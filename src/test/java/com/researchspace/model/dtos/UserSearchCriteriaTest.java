package com.researchspace.model.dtos;

import static org.junit.Assert.assertEquals;

import com.researchspace.core.testutil.CoreTestUtils;
import org.junit.BeforeClass;
import org.junit.Test;

public class UserSearchCriteriaTest {

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {}

  @Test
  public void testGetAllFields() {
    UserSearchCriteria crit = new UserSearchCriteria();
    crit.setAllFields(CoreTestUtils.getRandomName(300));
    assertEquals(255, crit.getAllFields().length());
  }
}
