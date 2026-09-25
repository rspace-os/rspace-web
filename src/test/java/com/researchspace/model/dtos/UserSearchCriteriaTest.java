package com.researchspace.model.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.core.testutil.CoreTestUtils;
import org.junit.jupiter.api.Test;

public class UserSearchCriteriaTest {

  @Test
  public void testGetAllFields() {
    UserSearchCriteria crit = new UserSearchCriteria();
    crit.setAllFields(CoreTestUtils.getRandomName(300));
    assertThat(crit.getAllFields()).hasSize(255);
  }
}
