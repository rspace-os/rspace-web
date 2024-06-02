package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class InventoryEditLocksControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void lockEditUnlockSample() throws Exception {

    // create users and a group
    User pi = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    User testUser = createAndSaveUser(getRandomName(10));
    initUsers(pi, testUser);
    createGroupForUsersWithDefaultPi(pi, testUser);

    String testUserApiKey = createApiKeyForuser(testUser);
    String piApiKey = createApiKeyForuser(pi);

    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(testUser);

    // lock the sample
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    testUserApiKey,
                    "/editLocks/" + sample.getGlobalId(),
                    testUser))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventoryEditLock sampleLock =
        mvcUtils.getFromJsonResponseBody(result, ApiInventoryEditLock.class);
    assertEquals(testUser.getUsername(), sampleLock.getOwner().getUsername());
    assertEquals(sample.getGlobalId(), sampleLock.getGlobalId());
    assertEquals(ApiInventoryEditLockStatus.LOCKED_OK, sampleLock.getStatus());

    // try locking again
    result =
        this.mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    testUserApiKey,
                    "/editLocks/" + sample.getGlobalId(),
                    testUser))
            .andReturn();
    assertNull(result.getResolvedException());
    sampleLock = mvcUtils.getFromJsonResponseBody(result, ApiInventoryEditLock.class);
    assertEquals(testUser.getUsername(), sampleLock.getOwner().getUsername());
    assertEquals(sample.getGlobalId(), sampleLock.getGlobalId());
    assertEquals(ApiInventoryEditLockStatus.WAS_ALREADY_LOCKED, sampleLock.getStatus());

    // try locking as a PI user
    result =
        this.mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE, piApiKey, "/editLocks/" + sample.getGlobalId(), pi))
            .andReturn();
    assertNull(result.getResolvedException());
    sampleLock = mvcUtils.getFromJsonResponseBody(result, ApiInventoryEditLock.class);
    assertEquals(testUser.getUsername(), sampleLock.getOwner().getUsername());
    assertEquals(sample.getGlobalId(), sampleLock.getGlobalId());
    assertEquals(ApiInventoryEditLockStatus.CANNOT_LOCK, sampleLock.getStatus());
    assertEquals(
        "Item is currently edited by another user (" + testUser.getUsername() + ")",
        sampleLock.getMessage());

    // unlock
    result =
        this.mockMvc
            .perform(
                createBuilderForDelete(
                    testUserApiKey, "/editLocks/" + sample.getGlobalId(), testUser))
            .andReturn();
    assertNull(result.getResolvedException());
    assertEquals(200, result.getResponse().getStatus());
  }
}
