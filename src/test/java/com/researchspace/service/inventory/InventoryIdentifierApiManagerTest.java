package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryDOIGeoLocation;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.datacite.DataCiteConnectorDummy;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryIdentifierApiManagerTest extends SpringTransactionalTest {

  private User user;

  @Autowired private DigitalObjectIdentifierDao doiDao;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    inventoryIdentifierApiMgr.setDataCiteConnector(new DataCiteConnectorDummy());
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(user);
  }

  @Test
  public void registerUpdateDeleteNewIdentifiers() {
    ApiSampleWithFullSubSamples createdSample = createComplexSampleForUser(user);
    assertEquals(1, createdSample.getTags().size());
    assertEquals(0, createdSample.getIdentifiers().size());
    ApiSubSample createdSubSample = createdSample.getSubSamples().get(0);
    assertEquals(0, createdSubSample.getIdentifiers().size());
    ApiContainer createdContainer = createBasicContainerForUser(user);
    assertEquals(0, createdContainer.getIdentifiers().size());

    ApiInventoryRecordInfo updatedSample =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdSample.getOid(), user);
    assertEquals(1, updatedSample.getTags().size()); // RSDEV-76
    assertEquals(1, updatedSample.getIdentifiers().size());
    ApiInventoryDOI sampleDoi = updatedSample.getIdentifiers().get(0);
    assertEquals("Material Sample", sampleDoi.getResourceType());
    assertEquals("draft", sampleDoi.getState());
    assertEquals(Boolean.FALSE, sampleDoi.getCustomFieldsOnPublicPage());
    assertNotNull(sampleDoi.getUrl());
    assertNull(sampleDoi.getSubjects());
    assertNull(sampleDoi.getDescriptions());
    assertNull(sampleDoi.getAlternateIdentifiers());
    assertNull(sampleDoi.getDates());
    assertEquals(createdSample.getOid().getIdString(), sampleDoi.getAssociatedGlobalId());

    // verify inventory record can be found by its identifier
    InventoryRecord sampleFoundByDoiId =
        inventoryIdentifierApiMgr.getInventoryRecordByIdentifierId(sampleDoi.getId());
    assertNotNull(sampleFoundByDoiId);
    assertEquals(createdSample.getGlobalId(), sampleFoundByDoiId.getOid().getIdString());
    assertEquals(1, sampleFoundByDoiId.getActiveIdentifiers().size());
    assertEquals(sampleDoi.getId(), sampleFoundByDoiId.getActiveIdentifiers().get(0).getId());
    assertNotNull(sampleFoundByDoiId.getActiveIdentifiers().get(0).getPublicLink());

    // update recommended details of identifier - as a part of item update
    ApiInventoryDOI doiUpdate = new ApiInventoryDOI();
    doiUpdate.setId(sampleDoi.getId());
    addOptionalPropertiesToIncomingDoi(doiUpdate);
    doiUpdate.setCustomFieldsOnPublicPage(true);
    List<ApiInventoryDOI> identifiersUpdate = List.of(doiUpdate);
    ApiSample sampleUpdate = new ApiSample();
    sampleUpdate.setId(updatedSample.getId());
    sampleUpdate.setIdentifiers(identifiersUpdate);

    // run the sample/identifiers update
    updatedSample = sampleApiMgr.updateApiSample(sampleUpdate, user);
    assertEquals(1, updatedSample.getIdentifiers().size());
    sampleDoi = updatedSample.getIdentifiers().get(0);
    assertEquals(1, sampleDoi.getSubjects().size());
    assertEquals(1, sampleDoi.getDescriptions().size());
    assertEquals(1, sampleDoi.getAlternateIdentifiers().size());
    assertEquals(1, sampleDoi.getDates().size());
    assertEquals(Boolean.TRUE, sampleDoi.getCustomFieldsOnPublicPage());
    // check geolocation save/retrieval
    assertEquals(2, sampleDoi.getGeoLocations().size());
    assertNull(sampleDoi.getGeoLocations().get(0).getGeoLocationInPolygonPoint());
    assertNotNull(sampleDoi.getGeoLocations().get(1).getGeoLocationInPolygonPoint());

    // delete the identifier
    updatedSample =
        inventoryIdentifierApiMgr.deleteAssociatedIdentifier(createdSample.getOid(), user);
    assertEquals(0, updatedSample.getIdentifiers().size());

    // confirm with subsample/container
    ApiInventoryRecordInfo updatedSubSample =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdSubSample.getOid(), user);
    assertEquals(1, updatedSubSample.getIdentifiers().size());
    assertEquals("Material Sample", updatedSubSample.getIdentifiers().get(0).getResourceType());
    ApiInventoryRecordInfo updatedContainer =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdContainer.getOid(), user);
    assertEquals(1, updatedContainer.getIdentifiers().size());
    assertEquals("Material Sample", updatedContainer.getIdentifiers().get(0).getResourceType());
  }

  @Test
  public void testRegisterBulkIdentifiers() {
    int initialDbSize = doiDao.getAll().size();
    List<ApiInventoryDOI> result = inventoryIdentifierApiMgr.registerBulkIdentifiers(3, user);

    assertEquals(3, result.size());

    assertNull(result.get(0).getAssociatedGlobalId());
    assertEquals("draft", result.get(0).getState());
    assertNull(result.get(1).getAssociatedGlobalId());
    assertEquals("draft", result.get(1).getState());
    assertNull(result.get(2).getAssociatedGlobalId());
    assertEquals("draft", result.get(2).getState());

    assertEquals(initialDbSize + 3, doiDao.getAll().size()); // make sure they are saved to DB

    // cleanup identifiers
    inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(result.get(0), user);
    inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(result.get(1), user);
    inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(result.get(2), user);
  }

  @Test
  public void testFindIdentifiersByStateAndCreator() {
    // GIVEN
    User anotherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api_another"));
    initialiseContentWithEmptyContent(anotherUser);
    ApiSampleWithFullSubSamples createdSample = createComplexSampleForUser(user);
    int initialDbSize = doiDao.getAll().size();

    inventoryIdentifierApiMgr.registerNewIdentifier(createdSample.getOid(), user);
    inventoryIdentifierApiMgr.registerBulkIdentifiers(2, user);
    inventoryIdentifierApiMgr.registerBulkIdentifiers(2, anotherUser);

    // WHEN
    List<ApiInventoryDOI> userAll =
        inventoryIdentifierApiMgr.findIdentifiersByStateAndOwner(null, user, null);
    List<ApiInventoryDOI> userAssociated =
        inventoryIdentifierApiMgr.findIdentifiersByStateAndOwner(null, user, true);
    List<ApiInventoryDOI> userAssociatedAndDraft =
        inventoryIdentifierApiMgr.findIdentifiersByStateAndOwner("draft", user, true);
    List<ApiInventoryDOI> userAssociatedAndRegistered =
        inventoryIdentifierApiMgr.findIdentifiersByStateAndOwner("registered", user, true);

    List<ApiInventoryDOI> userNotAssociated =
        inventoryIdentifierApiMgr.findIdentifiersByStateAndOwner(null, user, false);
    List<ApiInventoryDOI> userNotAssociatedAndDraft =
        inventoryIdentifierApiMgr.findIdentifiersByStateAndOwner("draft", user, false);
    List<ApiInventoryDOI> userNotAssociatedAndRegisterd =
        inventoryIdentifierApiMgr.findIdentifiersByStateAndOwner("registered", user, false);

    List<ApiInventoryDOI> anotherUserAll =
        inventoryIdentifierApiMgr.findIdentifiersByStateAndOwner(null, anotherUser, null);
    List<ApiInventoryDOI> anotherUserAssociated =
        inventoryIdentifierApiMgr.findIdentifiersByStateAndOwner(null, anotherUser, true);
    List<ApiInventoryDOI> anotherUserNotAssociated =
        inventoryIdentifierApiMgr.findIdentifiersByStateAndOwner(null, anotherUser, false);

    // THEN
    assertEquals(3, userAll.size());
    assertEquals(1, userAssociated.size());
    assertEquals(user, userAssociated.get(0).getOwner());
    assertEquals(1, userAssociatedAndDraft.size());
    assertTrue(userAssociatedAndRegistered.isEmpty());

    assertEquals(2, userNotAssociated.size());
    assertEquals(user, userNotAssociated.get(0).getOwner());
    assertEquals(user, userNotAssociated.get(1).getOwner());
    assertEquals(2, userNotAssociatedAndDraft.size());
    assertTrue(userNotAssociatedAndRegisterd.isEmpty());

    assertEquals(2, anotherUserAll.size());
    assertTrue(anotherUserAssociated.isEmpty());
    assertEquals(2, anotherUserNotAssociated.size());
    assertEquals(anotherUser, anotherUserNotAssociated.get(0).getOwner());
    assertEquals(anotherUser, anotherUserNotAssociated.get(1).getOwner());

    assertEquals(initialDbSize + 5, doiDao.getAll().size());

    // delete associated identifiers
    assertTrue(
        inventoryIdentifierApiMgr
            .deleteAssociatedIdentifier(createdSample.getOid(), user)
            .getIdentifiers()
            .isEmpty());
    // delete Unassociated identifiers
    assertTrue(
        inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(userNotAssociated.get(0), user));
    assertTrue(
        inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(userNotAssociated.get(1), user));
    assertTrue(
        inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(
            anotherUserNotAssociated.get(0), user));
    assertTrue(
        inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(
            anotherUserNotAssociated.get(1), user));
  }

  private void addOptionalPropertiesToIncomingDoi(ApiInventoryDOI doiUpdate) {
    ApiInventoryDOI.ApiInventoryDOISubject newDoiSubject =
        new ApiInventoryDOI.ApiInventoryDOISubject(
            "testSubject", "scheme", "schemeUri", "valueUri", "code");
    doiUpdate.setSubjects(List.of(newDoiSubject));
    ApiInventoryDOI.ApiInventoryDOIDescription newDoiDescription =
        new ApiInventoryDOI.ApiInventoryDOIDescription(
            "testDesc", ApiInventoryDOI.ApiInventoryDOIDescription.DoiDescriptionType.ABSTRACT);
    doiUpdate.setDescriptions(List.of(newDoiDescription));
    ApiInventoryDOI.ApiInventoryDOIAlternateIdentifier newDoiAlternateIdentifier =
        new ApiInventoryDOI.ApiInventoryDOIAlternateIdentifier("testAltId", "altIdType");
    doiUpdate.setAlternateIdentifiers(List.of(newDoiAlternateIdentifier));
    ApiInventoryDOI.ApiInventoryDOIDate newDoiDate =
        new ApiInventoryDOI.ApiInventoryDOIDate(
            "2023-07-27", ApiInventoryDOI.ApiInventoryDOIDate.DoiDateType.CREATED);
    doiUpdate.setDates(List.of(newDoiDate));

    ApiInventoryDOIGeoLocation newGeoLocationPoint =
        new ApiInventoryDOIGeoLocation("testLocation - point");
    newGeoLocationPoint.setGeoLocationPoint(
        new ApiInventoryDOIGeoLocation.ApiInventoryDOIGeoLocationPoint("2.1", "3.2"));
    newGeoLocationPoint.setGeoLocationBox(
        new ApiInventoryDOIGeoLocation.ApiInventoryDOIGeoLocationBox()); // UI may set empty box
    newGeoLocationPoint.setGeoLocationPolygon(
        List.of(
            new ApiInventoryDOIGeoLocation
                .ApiInventoryDOIGeoLocationPolygonPoint())); // UI may set empty polygon

    ApiInventoryDOIGeoLocation newGeoLocationBoxAndPolygon =
        new ApiInventoryDOIGeoLocation("testLocation - box/polygon");
    newGeoLocationBoxAndPolygon.setGeoLocationPoint(
        new ApiInventoryDOIGeoLocation.ApiInventoryDOIGeoLocationPoint(
            "", "")); // UI may set empty point
    newGeoLocationBoxAndPolygon.setGeoLocationBox(
        new ApiInventoryDOIGeoLocation.ApiInventoryDOIGeoLocationBox(
            "-68.211", "42.893", "41.050", "-71.032"));
    newGeoLocationBoxAndPolygon.setGeoLocationPolygon(
        List.of(
            new ApiInventoryDOIGeoLocation.ApiInventoryDOIGeoLocationPolygonPoint(
                "41.991", "-71.032"),
            new ApiInventoryDOIGeoLocation.ApiInventoryDOIGeoLocationPolygonPoint(
                "42.893", "-69.622"),
            new ApiInventoryDOIGeoLocation.ApiInventoryDOIGeoLocationPolygonPoint(
                "41.991", "-68.211"),
            new ApiInventoryDOIGeoLocation.ApiInventoryDOIGeoLocationPolygonPoint(
                "41.090", "-69.622"),
            new ApiInventoryDOIGeoLocation.ApiInventoryDOIGeoLocationPolygonPoint(
                "41.991", "-71.032")));
    newGeoLocationBoxAndPolygon.setGeoLocationInPolygonPoint(
        new ApiInventoryDOIGeoLocation.ApiInventoryDOIGeoLocationPoint("41", "42"));
    doiUpdate.setGeoLocations(List.of(newGeoLocationPoint, newGeoLocationBoxAndPolygon));
  }

  @Test
  public void registerAndPublishSubSampleIdentifier() {

    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(user);

    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(user);
    ApiSubSample createdSubSample = createdSample.getSubSamples().get(0);

    /* datacite client is mocked, we just check RSpace-side processing */

    // register
    ApiInventoryRecordInfo updatedSubSample =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdSubSample.getOid(), user);
    assertEquals(1, updatedSubSample.getIdentifiers().size());
    assertEquals("draft", updatedSubSample.getIdentifiers().get(0).getState());
    assertNotNull(updatedSubSample.getIdentifiers().get(0).getRsPublicId());
    assertNotNull(updatedSubSample.getIdentifiers().get(0).getUrl());
    assertNull(updatedSubSample.getIdentifiers().get(0).getPublicUrl());
    // publish
    updatedSubSample = inventoryIdentifierApiMgr.publishIdentifier(createdSubSample.getOid(), user);
    assertEquals(1, updatedSubSample.getIdentifiers().size());
    assertNotNull(updatedSubSample.getIdentifiers().get(0).getUrl());
    assertEquals("https://doi.org/null", updatedSubSample.getIdentifiers().get(0).getPublicUrl());
    // retract
    updatedSubSample = inventoryIdentifierApiMgr.retractIdentifier(createdSubSample.getOid(), user);
    assertEquals(1, updatedSubSample.getIdentifiers().size());
    assertNotNull(updatedSubSample.getIdentifiers().get(0).getUrl());
    assertEquals("https://doi.org/null", updatedSubSample.getIdentifiers().get(0).getPublicUrl());
  }

  @Test
  public void convertIncomingApiDoiToRSpaceDoiToDataCiteDoi() {
    // create rspace api doi
    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.setDoi("incomingDoi");
    apiDoi.setTitle("incomingTitle");
    addOptionalPropertiesToIncomingDoi(apiDoi); // adds subject/description/altId/date
    // convert to rspace db doi
    DigitalObjectIdentifier databaseDoi = new DigitalObjectIdentifier("testId", "testTitle");
    apiDoi.applyChangesToDatabaseDOI(databaseDoi);
    // convert to datacite doi
    DataCiteDoi dataCiteDoi = (new ApiInventoryDOI(databaseDoi)).convertToDataCiteDoi();
    // verify final datacite doi has all the expected values
    assertEquals("incomingDoi", dataCiteDoi.getId());
    assertEquals("incomingTitle", dataCiteDoi.getAttributes().getTitles().get(0).getTitle());
    assertEquals("dois", dataCiteDoi.getType());
    assertNotNull(dataCiteDoi.getAttributes().getSubjects());
    assertEquals(1, dataCiteDoi.getAttributes().getSubjects().size());
    assertNotNull(dataCiteDoi.getAttributes().getDescriptions());
    assertEquals(1, dataCiteDoi.getAttributes().getDescriptions().size());
    assertEquals("testDesc", dataCiteDoi.getAttributes().getDescriptions().get(0).getDescription());
    assertEquals(
        "Abstract", dataCiteDoi.getAttributes().getDescriptions().get(0).getDescriptionType());
    assertEquals(1, dataCiteDoi.getAttributes().getDescriptions().size());
    assertNotNull(dataCiteDoi.getAttributes().getAlternateIdentifiers());
    assertEquals(1, dataCiteDoi.getAttributes().getAlternateIdentifiers().size());
    assertNotNull(dataCiteDoi.getAttributes().getDates());
    assertEquals(1, dataCiteDoi.getAttributes().getDates().size());
    assertNotNull(dataCiteDoi.getAttributes().getGeoLocations());
    assertEquals(2, dataCiteDoi.getAttributes().getGeoLocations().size());
    assertEquals(
        "testLocation - point",
        dataCiteDoi.getAttributes().getGeoLocations().get(0).getGeoLocationPlace());
    assertNotNull(dataCiteDoi.getAttributes().getGeoLocations().get(0).getGeoLocationPoint());
    assertNull(dataCiteDoi.getAttributes().getGeoLocations().get(0).getGeoLocationBox());
    assertNull(dataCiteDoi.getAttributes().getGeoLocations().get(0).getGeoLocationPolygon());
    assertEquals(
        "testLocation - box/polygon",
        dataCiteDoi.getAttributes().getGeoLocations().get(1).getGeoLocationPlace());
    assertNull(dataCiteDoi.getAttributes().getGeoLocations().get(1).getGeoLocationPoint());
    assertNotNull(dataCiteDoi.getAttributes().getGeoLocations().get(1).getGeoLocationBox());
    assertNotNull(dataCiteDoi.getAttributes().getGeoLocations().get(1).getGeoLocationPolygon());
  }
}
