package com.researchspace.model.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.core.testutil.CoreTestUtils;
import org.junit.jupiter.api.Test;

public class CommunitySearchCriteriaTest {

  @Test
  public void testGetAllFields() {
    CommunitySearchCriteria crit = new CommunitySearchCriteria();
    crit.setDisplayName(CoreTestUtils.getRandomName(300));
    assertThat(crit.getDisplayName()).hasSize(255);
  }
}
