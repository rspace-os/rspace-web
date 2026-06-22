package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage of the link-target summary endpoint's permission freshness: an unshare must
 * be visible on the sharee's very next summary fetch, without a server restart. The unshare
 * notifies the sharee to refresh their cached Shiro authorisation ({@code
 * RecordSharingManagerImpl.doUnshare}) and the resolver applies pending notifications before
 * checking readability ({@code LinkTargetResolverImpl}).
 */
public class InventoryLinkTargetApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void unshareIsReflectedInSummaryWithoutServerRestart() throws Exception {
    User owner = createAndSaveUser("pi" + getRandomName(8), Constants.PI_ROLE);
    User viewer = createAndSaveUser(getRandomName(10));
    initUsers(owner, viewer);
    Group group = createGroupForPiAndUsers(owner, new User[] {owner, viewer});

    logoutAndLoginAs(owner);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(owner, "shared target");
    RecordGroupSharing share = shareRecordWithGroup(owner, group, doc);
    String docGlobalId = doc.getOid().getIdString();
    String viewerKey = createNewApiKeyForUser(viewer);

    // while shared, the viewer's summary is a full, readable one
    ApiInventoryLinkTargetSummary shared = fetchSummary(viewerKey, viewer, docGlobalId);
    assertTrue(shared.isReadable(), "shared target must be readable by the group member");
    assertNotNull(shared.getName());
    assertEquals("DOCUMENT", shared.getType());

    // the owner unshares; the viewer is NOT logged out and the server NOT restarted
    logoutAndLoginAs(owner);
    sharingHandler.unshare(share.getId(), owner);

    // the viewer's very next fetch must already see the redacted summary: the
    // unshare notification + refreshCacheIfNotified clear the viewer's stale
    // cached RECORD:READ grant on this request, not at some later restart
    ApiInventoryLinkTargetSummary unshared = fetchSummary(viewerKey, viewer, docGlobalId);
    assertFalse(unshared.isReadable(), "unshared target must stop being readable immediately");
    assertNull(unshared.getName(), "redacted summary must not disclose the name");
    assertNull(unshared.getType(), "redacted summary must not disclose the type");
    assertFalse(unshared.isDeleted(), "redacted summary must not disclose state");
    assertEquals(docGlobalId, unshared.getGlobalId());
  }

  @Test
  public void reShareRestoresReadableSummaryWithoutServerRestart() throws Exception {
    User owner = createAndSaveUser("pi" + getRandomName(8), Constants.PI_ROLE);
    User viewer = createAndSaveUser(getRandomName(10));
    initUsers(owner, viewer);
    Group group = createGroupForPiAndUsers(owner, new User[] {owner, viewer});

    logoutAndLoginAs(owner);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(owner, "reshared target");
    RecordGroupSharing share = shareRecordWithGroup(owner, group, doc);
    String docGlobalId = doc.getOid().getIdString();
    String viewerKey = createNewApiKeyForUser(viewer);

    assertTrue(fetchSummary(viewerKey, viewer, docGlobalId).isReadable());

    logoutAndLoginAs(owner);
    sharingHandler.unshare(share.getId(), owner);
    assertFalse(fetchSummary(viewerKey, viewer, docGlobalId).isReadable());

    // sharing again must restore the viewer's readable summary just as promptly
    logoutAndLoginAs(owner);
    shareRecordWithGroup(owner, group, doc);
    ApiInventoryLinkTargetSummary reshared = fetchSummary(viewerKey, viewer, docGlobalId);
    assertTrue(reshared.isReadable(), "re-shared target must be readable again without restart");
    assertNotNull(reshared.getName());
  }

  private ApiInventoryLinkTargetSummary fetchSummary(String apiKey, User user, String globalId)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/linkTargets/{globalId}/summary", user, globalId))
            .andExpect(status().isOk())
            .andReturn();
    return getFromJsonResponseBody(result, ApiInventoryLinkTargetSummary.class);
  }
}
