package com.researchspace.model.dtos;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.GroupType;
import java.lang.reflect.InvocationTargetException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GroupSearchCriteriaTest {
  GroupSearchCriteria filter;

  @Before
  public void setUp() throws Exception {
    filter = new GroupSearchCriteria();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testAllFiltersAreInitiallyOff() {
    assertallFiltersFalse();
  }

  @Test
  public void inputValidation() {
    filter.setDisplayName(getRandomName(300));
    assertEquals("display name not truncated", 255, filter.getDisplayName().length());

    filter.setUniqueName(getRandomName(300));
    assertEquals("unique name  not trunctated", 255, filter.getUniqueName().length());
  }

  @Test
  public void testSettingSwitchesOnFilters() {
    assertallFiltersFalse();
    filter.setDisplayName("any");
    filter.setGroupType(GroupType.LAB_GROUP);
    filter.setUniqueName("any");
    assertAllFiltersTrue();
    filter.reset(); // switches off all filters
    assertallFiltersFalse();
  }

  @Test
  public void RSPAC_886fix()
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    filter.setDisplayName("any");
    filter.setGroupType(GroupType.LAB_GROUP);
    filter.setUniqueName("any");
    filter.setCommunityId(null);
    String urlquery = filter.getURLQueryString();
    // should nto be i  url query if not set
    assertFalse(urlquery.contains("communityId=false"));
    assertFalse(urlquery.contains("communityId"));
  }

  private void assertAllFiltersTrue() {
    assertTrue(filter.isFilterByDisplayNameLike());
    assertTrue(filter.isFilterByUniqueName());
    assertTrue(filter.isFilterByGroupType());
  }

  private void assertallFiltersFalse() {
    assertFalse(filter.isFilterByDisplayNameLike());
    assertFalse(filter.isFilterByUniqueName());
    assertFalse(filter.isFilterByGroupType());
  }
}
