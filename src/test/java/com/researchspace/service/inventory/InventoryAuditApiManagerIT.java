package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList.ApiInventoryRecordRevision;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.SampleEntity;
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

    SampleEntity dbSample = sampleApiMgr.assertUserCanEditSample(sample.getId(), anyUser);
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
    assertEquals(10, apiSampleRev1.getFields().size());
  }

  @Test
  public void getInstrumentRevisions() {
    User anyUser = createInitAndLoginAnyUser();

    // create instrument
    ApiInstrument instrument = createBasicInstrumentForUser(anyUser, "original instrument");
    // update instrument name to create a second revision
    instrument.setName("updated instrument");
    instrumentApiMgr.updateApiInstrument(instrument, anyUser);

    Instrument dbInstrument =
        instrumentApiMgr.assertUserCanEditInstrument(instrument.getId(), anyUser);

    // get all instrument revisions
    ApiInventoryRecordRevisionList revisions =
        inventoryAuditMgr.getInventoryRecordRevisions(dbInstrument);
    assertEquals(2, revisions.getRevisionsCount());
    List<ApiInventoryRecordRevision> history = revisions.getRevisions();
    ApiInventoryRecordInfo rev1 = history.get(0).getRecord();
    assertEquals("original instrument", rev1.getName());
    ApiInventoryRecordInfo rev2 = history.get(1).getRecord();
    assertEquals("updated instrument", rev2.getName());

    // get 1st specific revision of the instrument
    long firstRevisionId = history.get(0).getRevisionId();
    ApiInstrument apiInstrumentRev1 =
        inventoryAuditMgr.getApiInstrumentRevision(instrument.getId(), firstRevisionId);
    assertNotNull(apiInstrumentRev1);
    assertEquals("original instrument", apiInstrumentRev1.getName());
    assertEquals(firstRevisionId, apiInstrumentRev1.getRevisionId());
    assertNotNull(apiInstrumentRev1.getGlobalId());

    // get 2nd pecific revision of the instrument
    long secondRevisionId = history.get(1).getRevisionId();
    ApiInstrument apiInstrumentRev2 =
        inventoryAuditMgr.getApiInstrumentRevision(instrument.getId(), secondRevisionId);
    assertNotNull(apiInstrumentRev1);
    assertEquals("updated instrument", apiInstrumentRev2.getName());
    assertEquals(secondRevisionId, apiInstrumentRev2.getRevisionId());
    assertNotNull(apiInstrumentRev2.getGlobalId());
  }
}
