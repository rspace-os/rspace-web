package com.researchspace.service.inventory;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Basic test class for wiring inventory audit manager, for real tests look into {@link
 * InventoryAuditApiManagerIT}.
 */
public class InventoryAuditApiManagerTest extends SpringTransactionalTest {

  private @Autowired InventoryAuditApiManager inventoryAuditMgr;

  @Test
  public void checkInventoryAuditManager() throws Exception {
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(user);
    Sample sample = sampleApiMgr.assertUserCanEditSample(basicSample.getId(), user);

    ApiInventoryRecordRevisionList revisions =
        inventoryAuditMgr.getInventoryRecordRevisions(sample);
    assertTrue(revisions.getRevisions().isEmpty()); // won't find, but should complete fine
    assertEquals(0, revisions.getRevisionsCount());

    ApiSample singleRevision = inventoryAuditMgr.getApiSampleRevision(basicSample.getId(), 1L);
    assertNull(singleRevision); // won't find, but should complete fine
  }
}
