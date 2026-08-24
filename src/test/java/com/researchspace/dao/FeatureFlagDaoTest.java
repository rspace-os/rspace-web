package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FeatureFlagDaoTest extends SpringTransactionalTest {

  private @Autowired FeatureFlagDao featureFlagDao;
  private User user;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user = createAndSaveRandomUser();
  }

  @Test
  public void managesBaselines() {
    featureFlagDao.upsertBaseline("firstFlag", true);
    featureFlagDao.upsertBaseline("secondFlag", false);

    assertEquals(
        Map.of("firstFlag", true, "secondFlag", false), featureFlagDao.getBaselineValues());

    featureFlagDao.upsertBaseline("firstFlag", false);
    assertFalse(featureFlagDao.getBaselineValues().get("firstFlag"));

    assertEquals(1, featureFlagDao.deleteBaselinesNotIn(List.of("firstFlag")));
    assertEquals(Map.of("firstFlag", false), featureFlagDao.getBaselineValues());

    assertEquals(1, featureFlagDao.deleteBaselinesNotIn(List.of()));
    assertTrue(featureFlagDao.getBaselineValues().isEmpty());
  }

  @Test
  public void managesUserOverrides() {
    featureFlagDao.setOverride(user.getId(), "firstFlag", true);
    featureFlagDao.setOverride(user.getId(), "secondFlag", false);

    assertEquals(
        Map.of("firstFlag", true, "secondFlag", false),
        featureFlagDao.getOverridesForUser(user.getId()));

    featureFlagDao.setOverride(user.getId(), "firstFlag", false);
    assertFalse(featureFlagDao.getOverridesForUser(user.getId()).get("firstFlag"));

    assertEquals(1, featureFlagDao.deleteOverridesNotIn(List.of("firstFlag")));
    assertEquals(Map.of("firstFlag", false), featureFlagDao.getOverridesForUser(user.getId()));

    featureFlagDao.clearOverride(user.getId(), "firstFlag");
    assertTrue(featureFlagDao.getOverridesForUser(user.getId()).isEmpty());
  }
}
