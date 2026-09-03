package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.webapp.integrations.datacite.DataCiteConnectorDummy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryIdentifierApiManagerIT extends RealTransactionSpringTestBase {

  @Autowired private DigitalObjectIdentifierDao doiDao;

  @BeforeEach
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

  /**
   * The public page serves the newest identifier revision, for both providers. This is
   * persistence-level on both counts - the revision comes out of an Envers query and the published
   * check out of the DAO's predicate - so neither is exercised by the unit tests over the predicate
   * alone (parallel review, PR 1066).
   *
   * <p>The rule used to differ by provider: DataCite held the page at the publication-time snapshot
   * so that a change made after publishing stayed private until a deliberate republish, while
   * B2INST served the newest revision because it has no republish to push one out with. That
   * distinction was removed deliberately. The user-visible consequence is asserted below: an
   * identifier-row change made while an IGSN is published, such as the customFieldsOnPublicPage
   * toggle, now reaches the public page immediately rather than waiting for a republish.
   *
   * <p>Writes the identifier row a second time while it is published, and again as B2INST, so a
   * regression either way fails here.
   */
  @Test
  public void publicPageServesTheNewestRevisionForBothProviders() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples createdSample = createComplexSampleForUser(user);

    ApiInventoryRecordInfo registered =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdSample.getOid(), user);
    ApiInventoryRecordInfo published =
        inventoryIdentifierApiMgr.publishIdentifier(registered.getOid(), user);
    Long doiId = published.getIdentifiers().get(0).getId();
    String publicLink =
        inventoryIdentifierApiMgr
            .getInventoryRecordByIdentifierId(doiId)
            .getActiveIdentifiers()
            .get(0)
            .getPublicLink();

    ApiInventoryRecordInfo justPublished =
        inventoryIdentifierApiMgr.findPublishedItemVersionByPublicLink(publicLink);
    assertNotNull(justPublished, "a freshly published identifier must be served publicly");
    assertFalse(justPublished.getIdentifiers().get(0).getCustomFieldsOnPublicPage());

    // A second, committed write to the identifier row while it is published: a new Envers revision
    // that no republish has pushed out.
    doInTransaction(
        () -> {
          DigitalObjectIdentifier doi = doiDao.get(doiId);
          doi.setCustomFieldsOnPublicPage(true);
          doiDao.save(doi);
        });

    /*
     * This identifier is an IGSN, so DataCite's rule applies - and that rule is now the same as
     * B2INST's. The later write IS public, with no republish. Before the rules were unified this
     * asserted the opposite, and the change of expectation here is the whole user-visible effect of
     * unifying them.
     */
    ApiInventoryRecordInfo igsnAfterSecondWrite =
        inventoryIdentifierApiMgr.findPublishedItemVersionByPublicLink(publicLink);
    assertNotNull(igsnAfterSecondWrite);
    assertTrue(igsnAfterSecondWrite.getIdentifiers().get(0).getCustomFieldsOnPublicPage());

    /*
     * The same history read as B2INST, which must now agree: the newest revision either way. Type
     * used to be the DAO's input for choosing between the two rules and is no longer consulted at
     * all, so this also pins that an accepted B2INST identifier is still served.
     */
    doInTransaction(
        () -> {
          DigitalObjectIdentifier doi = doiDao.get(doiId);
          doi.setType(DigitalObjectIdentifier.IdentifierType.PIDINST_B2INST);
          doi.setState("accepted");
          doiDao.save(doi);
        });

    ApiInventoryRecordInfo asB2inst =
        inventoryIdentifierApiMgr.findPublishedItemVersionByPublicLink(publicLink);
    assertNotNull(asB2inst, "an accepted B2INST identifier must be served publicly");
    assertEquals("accepted", asB2inst.getIdentifiers().get(0).getState());
    assertTrue(asB2inst.getIdentifiers().get(0).getCustomFieldsOnPublicPage());

    // A non-published newest revision hides the page again, whichever provider it belongs to.
    doInTransaction(
        () -> {
          DigitalObjectIdentifier doi = doiDao.get(doiId);
          doi.setState("declined");
          doiDao.save(doi);
        });

    assertNull(inventoryIdentifierApiMgr.findPublishedItemVersionByPublicLink(publicLink));
  }
}
