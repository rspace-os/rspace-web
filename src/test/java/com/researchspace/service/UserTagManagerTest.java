package com.researchspace.service;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.dtos.UserTagData;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserTagManagerTest extends SpringTransactionalTest {

  private @Autowired UserManager userManager;

  private @Autowired UserTagManager userTagManager;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testSaveRetrieveClearTags() {
    List<String> initialAllTags = userTagManager.getAllUserTags(null);

    User testUser = createAndSaveUserIfNotExists(getRandomName(5));
    List<UserTagData> userTags = userTagManager.getUserTags(List.of(testUser.getId()));
    assertEquals(1, userTags.size());
    assertEquals(testUser.getId(), userTags.get(0).getUserId());
    assertEquals(0, userTags.get(0).getUserTags().size());

    userTagManager.saveUserTags(
        List.of(new UserTagData(testUser.getId(), List.of("testTag", "pi"))));
    userTags = userTagManager.getUserTags(List.of(testUser.getId()));
    assertEquals(1, userTags.size());
    assertEquals(testUser.getId(), userTags.get(0).getUserId());
    assertEquals(2, userTags.get(0).getUserTags().size());
    assertEquals("testTag", userTags.get(0).getUserTags().get(0));
    assertEquals("pi", userTags.get(0).getUserTags().get(1));

    List<String> finalAllTags = userTagManager.getAllUserTags(null);
    assertTrue(initialAllTags.size() < finalAllTags.size());
    assertTrue(finalAllTags.contains("testTag"));
    assertTrue(finalAllTags.contains("pi"));

    userTagManager.saveUserTags(
        List.of(new UserTagData(testUser.getId(), Collections.emptyList())));
    userTags = userTagManager.getUserTags(List.of(testUser.getId()));
    assertEquals(1, userTags.size());
    assertEquals(testUser.getId(), userTags.get(0).getUserId());
    assertEquals(0, userTags.get(0).getUserTags().size());
  }
}
