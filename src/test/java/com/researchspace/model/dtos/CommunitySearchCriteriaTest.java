package com.researchspace.model.dtos;

import static org.junit.Assert.assertEquals;

import com.researchspace.core.testutil.CoreTestUtils;
import org.junit.BeforeClass;
import org.junit.Test;

public class CommunitySearchCriteriaTest {

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {}

  @Test
  public void testGetAllFields() {
    CommunitySearchCriteria crit = new CommunitySearchCriteria();
    crit.setDisplayName(CoreTestUtils.getRandomName(300));
    assertEquals(255, crit.getDisplayName().length());
  }
}
