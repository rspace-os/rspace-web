package com.researchspace.model.dtos;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.GroupType;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GroupSearchCriteriaTest {
  GroupSearchCriteria filter;

  @BeforeEach
  public void setUp() throws Exception {
    filter = new GroupSearchCriteria();
  }

  @Test
  public void testAllFiltersAreInitiallyOff() {
    assertallFiltersFalse();
  }

  @Test
  public void inputValidation() {
    filter.setDisplayName(getRandomName(300));
    assertEquals(255, filter.getDisplayName().length(), "display name not truncated");

    filter.setUniqueName(getRandomName(300));
    assertEquals(255, filter.getUniqueName().length(), "unique name  not trunctated");
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
