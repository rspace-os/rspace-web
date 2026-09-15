package com.researchspace.model.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.core.testutil.CoreTestUtils;
import org.junit.jupiter.api.Test;

public class UserSearchCriteriaTest {

  @Test
  public void testGetAllFields() {
    UserSearchCriteria crit = new UserSearchCriteria();
    crit.setAllFields(CoreTestUtils.getRandomName(300));
    assertEquals(255, crit.getAllFields().length());
  }
}
