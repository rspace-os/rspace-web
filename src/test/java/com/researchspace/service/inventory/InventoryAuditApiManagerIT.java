package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList.ApiInventoryRecordRevision;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * This class extends RealTransactionSpringTestBase as auditing only happens after a transaction is
 * really committed to the database.
 */
public class InventoryAuditApiManagerIT extends RealTransactionSpringTestBase {

  private @Autowired InventoryAuditApiManager inventoryAuditMgr;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void getSampleSubSampleRevisions() {
    User anyUser = createInitAndLoginAnyUser();

    // create sample
    ApiSampleWithFullSubSamples sample = createComplexSampleForUser(anyUser);
    ApiSubSample subSample = sample.getSubSamples().get(0);
    // add subsample note
    subSampleApiMgr.addSubSampleNote(
        subSample.getId(), new ApiSubSampleNote("another note"), anyUser);
    // update sample name
    sample.setName("updated sample");
    sampleApiMgr.updateApiSample(sample, anyUser);
    // update subsample name
    subSample.setName("updated subSample");
    subSampleApiMgr.updateApiSubSample(subSample, anyUser);

    Sample dbSample = sampleApiMgr.assertUserCanEditSample(sample.getId(), anyUser);
    SubSample dbSubSample = subSampleApiMgr.assertUserCanEditSubSample(subSample.getId(), anyUser);

    // get all subsample revisions
    ApiInventoryRecordRevisionList subSampleRevisions =
        inventoryAuditMgr.getInventoryRecordRevisions(dbSubSample);
    assertEquals(3, subSampleRevisions.getRevisionsCount());
    List<ApiInventoryRecordRevision> subSampleHistory = subSampleRevisions.getRevisions();
    ApiInventoryRecordInfo ssRev1 = subSampleHistory.get(0).getRecord();
    assertEquals("mySubSample", ssRev1.getName());
    ApiInventoryRecordInfo ssRev2 = subSampleHistory.get(1).getRecord();
    assertEquals("mySubSample", ssRev2.getName());
    ApiInventoryRecordInfo ssRev3 = subSampleHistory.get(2).getRecord();
    assertEquals("updated subSample", ssRev3.getName());

    // get specific revision of the subsample
    long subSampleSecondRevisionId = subSampleHistory.get(1).getRevisionId();
    ApiSubSample apiSubSampleRev2 =
        inventoryAuditMgr.getApiSubSampleRevision(subSample.getId(), subSampleSecondRevisionId);
    assertEquals("mySubSample", apiSubSampleRev2.getName());
    assertEquals(subSampleSecondRevisionId, apiSubSampleRev2.getRevisionId());
    assertEquals(1, apiSubSampleRev2.getExtraFields().size());
    assertEquals(2, apiSubSampleRev2.getNotes().size());

    // get all sample revisions
    ApiInventoryRecordRevisionList sampleRevisions =
        inventoryAuditMgr.getInventoryRecordRevisions(dbSample);
    List<ApiInventoryRecordRevision> sampleHistory = sampleRevisions.getRevisions();
    assertEquals(2, sampleHistory.size());
    ApiInventoryRecordInfo saRev1 = sampleHistory.get(0).getRecord();
    assertEquals("myComplexSample", saRev1.getName());
    ApiInventoryRecordInfo saRev2 = sampleHistory.get(1).getRecord();
    assertEquals("updated sample", saRev2.getName());

    // get specific revision of the sample
    long sampleFirstRevisionId = sampleHistory.get(0).getRevisionId();
    ApiSample apiSampleRev1 =
        inventoryAuditMgr.getApiSampleRevision(sample.getId(), sampleFirstRevisionId);
    assertEquals("myComplexSample", apiSampleRev1.getName());
    assertEquals(sampleFirstRevisionId, apiSampleRev1.getRevisionId());
    assertEquals(1, apiSampleRev1.getExtraFields().size());
    assertEquals(9, apiSampleRev1.getFields().size());
  }
}
