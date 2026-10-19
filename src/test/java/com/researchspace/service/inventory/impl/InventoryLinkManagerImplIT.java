package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The pinned-version rule against real audit data (RSDEV-1530). Envers writes audit rows only on a
 * real commit, so these cases cannot live in the auto-rolled-back {@code
 * InventoryLinkManagerImplTest}, where every pin would read as missing.
 */
public class InventoryLinkManagerImplIT extends RealTransactionSpringTestBase {

  @Autowired private InventoryLinkManager linkManager;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  private static ApiInventoryLink linkTo(String targetGlobalId) {
    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId(targetGlobalId);
    return api;
  }

  @Test
  public void createLinkPinnedToAVersionTheTargetHasStoresThePinAndItsRevision() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);

    // a newly created sample is at version 1
    InventoryLink saved = linkManager.createLink(linkTo(target.getGlobalId() + "v1"), user);

    assertEquals(Long.valueOf(1), saved.getVersionPin());
    assertEquals(target.getId(), saved.getTargetDbId());
    assertNotNull(
        saved.getTargetRevisionId(), "a pin to a version that exists captures its revision");
  }

  @Test
  public void createLinkPinnedToAVersionTheTargetLacksIsRefused() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);
    ApiInventoryLink api = linkTo(target.getGlobalId() + "v7");

    ApiRuntimeException refused =
        assertThrows(ApiRuntimeException.class, () -> linkManager.createLink(api, user));

    assertEquals("errors.inventory.field.linkTargetVersionNotFound", refused.getErrorCode());
  }

  @Test
  public void canCreateLinkFollowsTheSameRule() {
    User owner = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(owner);

    assertTrue(linkManager.canCreateLink(linkTo(target.getGlobalId() + "v1"), owner));
    assertFalse(linkManager.canCreateLink(linkTo(target.getGlobalId() + "v7"), owner));
  }

  @Test
  public void aStrangerPinningAMissingVersionIsRefusedAsNotFoundNotAsAMissingVersion() {
    // ADR 0002: the READ check comes first, so nothing says whether version 7 exists
    User owner = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(owner);
    User stranger = createInitAndLoginAnyUser();
    ApiInventoryLink api = linkTo(target.getGlobalId() + "v7");

    ApiRuntimeException refused =
        assertThrows(ApiRuntimeException.class, () -> linkManager.createLink(api, stranger));

    assertEquals("errors.inventory.field.linkTargetNotFound", refused.getErrorCode());
  }
}
