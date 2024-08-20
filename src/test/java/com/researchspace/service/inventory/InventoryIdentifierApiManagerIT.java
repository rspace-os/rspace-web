package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.webapp.integrations.datacite.DataCiteConnectorDummy;
import org.junit.Before;
import org.junit.Test;

public class InventoryIdentifierApiManagerIT extends RealTransactionSpringTestBase {

  @Before
  public void setUp() throws Exception {
    super.setUp();
    inventoryIdentifierApiMgr.setDataCiteConnector(new DataCiteConnectorDummy());
  }

  @Test
  public void retrieveLastPublishedVersionOfItemWithPublicLink() {

    User user = createInitAndLoginAnyUser();

    ApiSampleWithFullSubSamples createdSample = createComplexSampleForUser(user);
    assertEquals(10, createdSample.getFields().size());
    assertEquals(1, createdSample.getTags().size());
    assertEquals(0, createdSample.getIdentifiers().size());

    ApiInventoryRecordInfo updatedSample =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdSample.getOid(), user);
    assertEquals(10, ((ApiSample) updatedSample).getFields().size());
    assertEquals(1, updatedSample.getTags().size());
    assertEquals(1, updatedSample.getIdentifiers().size());
    ApiInventoryDOI sampleDoi = updatedSample.getIdentifiers().get(0);
    assertEquals("Material Sample", sampleDoi.getResourceType());
    assertEquals("draft", sampleDoi.getState());
    assertNotNull(sampleDoi.getUrl());

    // verify inventory record can be found by its identifier
    InventoryRecord sampleFoundByDoiId =
        inventoryIdentifierApiMgr.getInventoryRecordByIdentifierId(sampleDoi.getId());
    String publicLink = sampleFoundByDoiId.getActiveIdentifiers().get(0).getPublicLink();
    assertNotNull(sampleFoundByDoiId);
    assertEquals(createdSample.getGlobalId(), sampleFoundByDoiId.getOid().getIdString());
    assertEquals(1, sampleFoundByDoiId.getActiveIdentifiers().size());
    assertEquals(sampleDoi.getId(), sampleFoundByDoiId.getActiveIdentifiers().get(0).getId());
    // identifier was not published yet, so inventory record cannot be found by public link
    assertNull(inventoryIdentifierApiMgr.findPublishedItemVersionByPublicLink(publicLink));

    // publish the identifier
    updatedSample = inventoryIdentifierApiMgr.publishIdentifier(updatedSample.getOid(), user);
    sampleDoi = updatedSample.getIdentifiers().get(0);
    assertEquals("findable", sampleDoi.getState());
    // identifier was published and should be findable by public link now
    ApiInventoryRecordInfo publishedRecord =
        inventoryIdentifierApiMgr.findPublishedItemVersionByPublicLink(publicLink);
    assertNotNull(publishedRecord);
    assertEquals("myComplexSample", publishedRecord.getName());
    assertEquals(10, ((ApiSample) publishedRecord).getFields().size());
    assertEquals(1, publishedRecord.getTags().size());
    assertEquals(1, publishedRecord.getIdentifiers().size());

    // update the sample's name
    ApiSample sampleUpdate = new ApiSample();
    sampleUpdate.setId(updatedSample.getId());
    sampleUpdate.setName("updated myComplexSample");
    sampleApiMgr.updateApiSample(sampleUpdate, user);

    // public link should return state from the moment of publishing
    publishedRecord = inventoryIdentifierApiMgr.findPublishedItemVersionByPublicLink(publicLink);
    assertNotNull(publishedRecord);
    assertEquals("myComplexSample", publishedRecord.getName());

    // retract the identifier
    updatedSample = inventoryIdentifierApiMgr.retractIdentifier(updatedSample.getOid(), user);
    sampleDoi = updatedSample.getIdentifiers().get(0);
    assertEquals("registered", sampleDoi.getState());

    // retracted identifier means item details should not be retrievable by public link
    assertNull(inventoryIdentifierApiMgr.findPublishedItemVersionByPublicLink(publicLink));

    // republish the identifier
    updatedSample = inventoryIdentifierApiMgr.publishIdentifier(updatedSample.getOid(), user);
    sampleDoi = updatedSample.getIdentifiers().get(0);
    assertEquals("findable", sampleDoi.getState());
    publishedRecord = inventoryIdentifierApiMgr.findPublishedItemVersionByPublicLink(publicLink);
    assertNotNull(publishedRecord);
    assertEquals("updated myComplexSample", publishedRecord.getName());
  }
}
