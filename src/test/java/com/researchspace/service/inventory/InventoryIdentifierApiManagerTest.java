package com.researchspace.service.inventory;

import static com.researchspace.webapp.integrations.datacite.DataCiteConnectorDummy.DUMMY_VALID_DOI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryDOIGeoLocation;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.datacite.DataCiteConnectorDummy;
import com.researchspace.webapp.integrations.datacite.DataCiteConnectorDummyError;
import java.util.List;
import javax.naming.InvalidNameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryIdentifierApiManagerTest extends SpringTransactionalTest {

  private User user;

  @Autowired private DigitalObjectIdentifierDao doiDao;
  @Autowired private InventoryIdentifierApiManager inventoryIdentifierApiMgr;

  private DataCiteConnectorDummy dataCiteConnectorDummy;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    dataCiteConnectorDummy = new DataCiteConnectorDummy();
    inventoryIdentifierApiMgr.setDataCiteConnector(dataCiteConnectorDummy);
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(user);
  }

  @Test
  public void registerNewIdentifierForInstrumentUsesPidinstWorkflow() {
    ApiInstrument createdInstrument = createBasicInstrumentForUser(user);
    assertThat(createdInstrument.getIdentifiers()).isEmpty();

    ApiInventoryRecordInfo updatedInstrument =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdInstrument.getOid(), user);
    assertThat(updatedInstrument.getIdentifiers()).hasSize(1);

    ApiInventoryDOI createdDoi = updatedInstrument.getIdentifiers().get(0);
    assertEquals("PIDINST_DATACITE", createdDoi.getDoiType());
    assertEquals("Instrument", createdDoi.getResourceType());
    assertEquals("Instrument", createdDoi.getResourceTypeGeneral());
    assertEquals(createdInstrument.getGlobalId(), createdDoi.getAssociatedGlobalId());
    assertEquals(InventorySettingType.PIDINST, dataCiteConnectorDummy.getLastSettingTypeUsed());
    assertEquals(IdentifierType.PIDINST_DATACITE, doiDao.get(createdDoi.getId()).getType());
  }

  @Test
  public void registerNewIdentifierForSampleStaysIgsn() {
    ApiSampleWithFullSubSamples createdSample = createComplexSampleForUser(user);

    ApiInventoryRecordInfo updatedSample =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdSample.getOid(), user);

    ApiInventoryDOI createdDoi = updatedSample.getIdentifiers().get(0);
    assertEquals("IGSN_DATACITE", createdDoi.getDoiType());
    assertEquals("Material Sample", createdDoi.getResourceType());
    assertEquals("PhysicalObject", createdDoi.getResourceTypeGeneral());
    assertEquals(InventorySettingType.IGSN, dataCiteConnectorDummy.getLastSettingTypeUsed());
    assertEquals(IdentifierType.IGSN_DATACITE, doiDao.get(createdDoi.getId()).getType());
  }

  @Test
  public void registerInstrumentIdentifierWhenPidinstDisabledThrows() {
    dataCiteConnectorDummy.setEnabled(InventorySettingType.PIDINST, false);
    ApiInstrument createdInstrument = createBasicInstrumentForUser(user);
    GlobalIdentifier instrumentId = createdInstrument.getOid();

    assertThrows(
        UnsupportedOperationException.class,
        () -> inventoryIdentifierApiMgr.registerNewIdentifier(instrumentId, user));
    assertNull(dataCiteConnectorDummy.getDoiSentToDatacite());
  }

  @Test
  public void instrumentIdentifierLifecycleUsesPidinstClient() {
    ApiInstrument createdInstrument = createBasicInstrumentForUser(user);
    ApiInventoryRecordInfo updatedInstrument =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdInstrument.getOid(), user);
    GlobalIdentifier instrumentOid = createdInstrument.getOid();

    ApiInventoryRecordInfo publishedInstrument =
        inventoryIdentifierApiMgr.publishIdentifier(instrumentOid, user);
    assertEquals("findable", publishedInstrument.getIdentifiers().get(0).getState());
    assertEquals(InventorySettingType.PIDINST, dataCiteConnectorDummy.getLastSettingTypeUsed());

    ApiInventoryRecordInfo retractedInstrument =
        inventoryIdentifierApiMgr.retractIdentifier(instrumentOid, user);
    assertEquals("registered", retractedInstrument.getIdentifiers().get(0).getState());
    assertEquals(InventorySettingType.PIDINST, dataCiteConnectorDummy.getLastSettingTypeUsed());

    ApiInventoryRecordInfo deletedIdentifierInstrument =
        inventoryIdentifierApiMgr.deleteAssociatedIdentifier(instrumentOid, user);
    assertThat(deletedIdentifierInstrument.getIdentifiers()).isEmpty();
    assertEquals(InventorySettingType.PIDINST, dataCiteConnectorDummy.getLastSettingTypeUsed());
  }

  @Test
  public void registerIdentifierForInstrumentTemplateUnsupported() {
    ApiInstrumentTemplate createdTemplate = createBasicInstrumentTemplateForUser(user);
    GlobalIdentifier templateId = createdTemplate.getOid();

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> inventoryIdentifierApiMgr.registerNewIdentifier(templateId, user));
    assertThat(iae.getMessage()).as(iae.getMessage()).contains("unsupported type for minting");
    // the type check runs before any DataCite call, so no draft DOI was leaked
    assertNull(dataCiteConnectorDummy.getDoiSentToDatacite());
  }

  @Test
  public void assignIgsnIdentifierToInstrumentRejected() {
    List<ApiInventoryDOI> allocatedIgsns =
        inventoryIdentifierApiMgr.registerBulkIdentifiers(1, user);
    ApiInstrument createdInstrument = createBasicInstrumentForUser(user);
    GlobalIdentifier instrumentId = createdInstrument.getOid();
    Long identifierId = allocatedIgsns.get(0).getId();

    assertThrows(
        IllegalArgumentException.class,
        () -> inventoryIdentifierApiMgr.assignIdentifier(instrumentId, identifierId, user));

    // cleanup the unassociated allocated identifier
    inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(allocatedIgsns.get(0), user);
  }

  @Test
  public void registerUpdateDeleteNewIdentifiers() {
    ApiSampleWithFullSubSamples createdSample = createComplexSampleForUser(user);
    assertThat(createdSample.getTags()).hasSize(1);
    assertThat(createdSample.getIdentifiers()).isEmpty();
    ApiSubSample createdSubSample = createdSample.getSubSamples().get(0);
    assertThat(createdSubSample.getIdentifiers()).isEmpty();
    ApiContainer createdContainer = createBasicContainerForUser(user);
    assertThat(createdContainer.getIdentifiers()).isEmpty();

    ApiInventoryRecordInfo updatedSample =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdSample.getOid(), user);
    assertThat(updatedSample.getTags()).hasSize(1); // RSDEV-76
    assertThat(updatedSample.getIdentifiers()).hasSize(1);
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
    assertThat(sampleFoundByDoiId.getActiveIdentifiers()).hasSize(1);
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
    assertThat(updatedSample.getIdentifiers()).hasSize(1);
    sampleDoi = updatedSample.getIdentifiers().get(0);
    assertThat(sampleDoi.getSubjects()).hasSize(1);
    assertThat(sampleDoi.getDescriptions()).hasSize(1);
    assertThat(sampleDoi.getAlternateIdentifiers()).hasSize(1);
    assertThat(sampleDoi.getDates()).hasSize(1);
    assertEquals(Boolean.TRUE, sampleDoi.getCustomFieldsOnPublicPage());
    // check geolocation save/retrieval
    assertThat(sampleDoi.getGeoLocations()).hasSize(2);
    assertNull(sampleDoi.getGeoLocations().get(0).getGeoLocationInPolygonPoint());
    assertNotNull(sampleDoi.getGeoLocations().get(1).getGeoLocationInPolygonPoint());

    // delete the identifier
    updatedSample =
        inventoryIdentifierApiMgr.deleteAssociatedIdentifier(createdSample.getOid(), user);
    assertThat(updatedSample.getIdentifiers()).isEmpty();

    // confirm with subsample/container
    ApiInventoryRecordInfo updatedSubSample =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdSubSample.getOid(), user);
    assertThat(updatedSubSample.getIdentifiers()).hasSize(1);
    assertEquals("Material Sample", updatedSubSample.getIdentifiers().get(0).getResourceType());
    ApiInventoryRecordInfo updatedContainer =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdContainer.getOid(), user);
    assertThat(updatedContainer.getIdentifiers()).hasSize(1);
    assertEquals("Material Sample", updatedContainer.getIdentifiers().get(0).getResourceType());
  }

  @Test
  public void testRegisterBulkIdentifiers() {
    int initialDbSize = doiDao.getAll().size();
    List<ApiInventoryDOI> result = inventoryIdentifierApiMgr.registerBulkIdentifiers(3, user);

    assertThat(result).hasSize(3);

    assertNull(result.get(0).getAssociatedGlobalId());
    assertEquals("draft", result.get(0).getState());
    assertNull(result.get(1).getAssociatedGlobalId());
    assertEquals("draft", result.get(1).getState());
    assertNull(result.get(2).getAssociatedGlobalId());
    assertEquals("draft", result.get(2).getState());

    assertThat(doiDao.getAll()).hasSize(initialDbSize + 3); // make sure they are saved to DB

    // cleanup identifiers
    inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(result.get(0), user);
    inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(result.get(1), user);
    inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(result.get(2), user);
  }

  @Test
  public void testRegisterBulkIdentifiersThrowsError() {
    inventoryIdentifierApiMgr.setDataCiteConnector(new DataCiteConnectorDummyError());
    assertThrows(
        DataCiteConnectionException.class,
        () -> inventoryIdentifierApiMgr.registerBulkIdentifiers(3, user));
  }

  @Test
  public void testFindIdentifiersByQuery() throws InvalidNameException {
    // GIVEN
    User anotherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api_another"));
    initialiseContentWithEmptyContent(anotherUser);
    ApiSampleWithFullSubSamples createdSample = createComplexSampleForUser(user);
    int initialDbSize = doiDao.getAll().size();

    inventoryIdentifierApiMgr.registerNewIdentifier(createdSample.getOid(), user);
    inventoryIdentifierApiMgr.registerBulkIdentifiers(2, user);
    inventoryIdentifierApiMgr.registerBulkIdentifiers(2, anotherUser);

    // WHEN we search for any "valid" DOI --> THEN assert the result
    List<ApiInventoryDOI> userExistingDoiAssociatedAndDraft =
        inventoryIdentifierApiMgr.findIdentifiers("draft", true, DUMMY_VALID_DOI, true, user);
    assertThat(userExistingDoiAssociatedAndDraft).hasSize(1);
    userExistingDoiAssociatedAndDraft =
        inventoryIdentifierApiMgr.findIdentifiers(
            "draft", true, "https://doi.org/" + DUMMY_VALID_DOI, true, user);
    assertThat(userExistingDoiAssociatedAndDraft).hasSize(1);
    userExistingDoiAssociatedAndDraft =
        inventoryIdentifierApiMgr.findIdentifiers(
            "draft", true, "doi.org/" + DUMMY_VALID_DOI, true, user);
    assertThat(userExistingDoiAssociatedAndDraft).hasSize(1);
    userExistingDoiAssociatedAndDraft =
        inventoryIdentifierApiMgr.findIdentifiers(
            "draft", true, DUMMY_VALID_DOI.substring(0, DUMMY_VALID_DOI.length() - 3), true, user);
    assertThat(userExistingDoiAssociatedAndDraft).hasSize(1);
    userExistingDoiAssociatedAndDraft =
        inventoryIdentifierApiMgr.findIdentifiers(
            "draft",
            true,
            "https://doi.org/" + DUMMY_VALID_DOI.substring(0, DUMMY_VALID_DOI.length() - 3),
            true,
            user);
    assertThat(userExistingDoiAssociatedAndDraft).hasSize(1);
    userExistingDoiAssociatedAndDraft =
        inventoryIdentifierApiMgr.findIdentifiers(
            "draft",
            true,
            "doi.org/" + DUMMY_VALID_DOI.substring(0, DUMMY_VALID_DOI.length() - 3),
            true,
            user);
    assertThat(userExistingDoiAssociatedAndDraft).hasSize(1);
    userExistingDoiAssociatedAndDraft =
        inventoryIdentifierApiMgr.findIdentifiers(
            "draft",
            true,
            "doi.org/" + DUMMY_VALID_DOI.substring(0, DUMMY_VALID_DOI.length() - 3),
            false, // do not allow substring search
            user);
    assertThat(userExistingDoiAssociatedAndDraft).isEmpty();

    // WHEN we search for any "NON valid" DOI --> THEN assert the result
    List<ApiInventoryDOI> userNotExistingDoiAssociatedAndDraft =
        inventoryIdentifierApiMgr.findIdentifiers(
            "draft", true, "NOT_" + DUMMY_VALID_DOI, true, user);
    assertThat(userNotExistingDoiAssociatedAndDraft).isEmpty();

    assertThat(doiDao.getAll()).hasSize(initialDbSize + 5);

    // delete associated identifiers
    assertThat(
            inventoryIdentifierApiMgr
                .deleteAssociatedIdentifier(createdSample.getOid(), user)
                .getIdentifiers())
        .isEmpty();
    // delete Unassociated identifiers
    List<ApiInventoryDOI> anotherUserNotAssociated =
        inventoryIdentifierApiMgr.findIdentifiers(null, false, null, true, anotherUser);
    List<ApiInventoryDOI> userNotAssociated =
        inventoryIdentifierApiMgr.findIdentifiers(null, false, null, true, user);
    assertTrue(
        inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(userNotAssociated.get(0), user));
    assertTrue(
        inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(userNotAssociated.get(1), user));
    assertTrue(
        inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(
            anotherUserNotAssociated.get(0), anotherUser));
    assertTrue(
        inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(
            anotherUserNotAssociated.get(1), anotherUser));
  }

  @Test
  public void testFindIdentifiersByStateAndCreator() throws InvalidNameException {
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
        inventoryIdentifierApiMgr.findIdentifiers(null, null, null, true, user);
    List<ApiInventoryDOI> userAssociated =
        inventoryIdentifierApiMgr.findIdentifiers(null, true, null, true, user);
    List<ApiInventoryDOI> userAssociatedAndDraft =
        inventoryIdentifierApiMgr.findIdentifiers("draft", true, null, true, user);
    List<ApiInventoryDOI> userAssociatedAndRegistered =
        inventoryIdentifierApiMgr.findIdentifiers("registered", true, null, true, user);
    List<ApiInventoryDOI> userNotAssociated =
        inventoryIdentifierApiMgr.findIdentifiers(null, false, null, true, user);
    List<ApiInventoryDOI> userNotAssociatedAndDraft =
        inventoryIdentifierApiMgr.findIdentifiers("draft", false, null, true, user);
    List<ApiInventoryDOI> userNotAssociatedAndRegisterd =
        inventoryIdentifierApiMgr.findIdentifiers("registered", false, null, true, user);

    List<ApiInventoryDOI> anotherUserAll =
        inventoryIdentifierApiMgr.findIdentifiers(null, null, null, true, anotherUser);
    List<ApiInventoryDOI> anotherUserAssociated =
        inventoryIdentifierApiMgr.findIdentifiers(null, true, null, true, anotherUser);
    List<ApiInventoryDOI> anotherUserNotAssociated =
        inventoryIdentifierApiMgr.findIdentifiers(null, false, null, true, anotherUser);

    // THEN
    assertThat(userAll).hasSize(3);
    assertThat(userAssociated).hasSize(1);
    assertEquals(user, userAssociated.get(0).getOwner());
    assertThat(userAssociatedAndDraft).hasSize(1);
    assertThat(userAssociatedAndRegistered).isEmpty();

    assertThat(userNotAssociated).hasSize(2);
    assertEquals(user, userNotAssociated.get(0).getOwner());
    assertEquals(user, userNotAssociated.get(1).getOwner());
    assertThat(userNotAssociatedAndDraft).hasSize(2);
    assertThat(userNotAssociatedAndRegisterd).isEmpty();

    assertThat(anotherUserAll).hasSize(2);
    assertThat(anotherUserAssociated).isEmpty();
    assertThat(anotherUserNotAssociated).hasSize(2);
    assertEquals(anotherUser, anotherUserNotAssociated.get(0).getOwner());
    assertEquals(anotherUser, anotherUserNotAssociated.get(1).getOwner());

    assertThat(doiDao.getAll()).hasSize(initialDbSize + 5);

    // delete associated identifiers
    assertThat(
            inventoryIdentifierApiMgr
                .deleteAssociatedIdentifier(createdSample.getOid(), user)
                .getIdentifiers())
        .isEmpty();
    // delete Unassociated identifiers
    assertTrue(
        inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(userNotAssociated.get(0), user));
    assertTrue(
        inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(userNotAssociated.get(1), user));
    assertTrue(
        inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(
            anotherUserNotAssociated.get(0), anotherUser));
    assertTrue(
        inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(
            anotherUserNotAssociated.get(1), anotherUser));
  }

  @Test
  public void testAssignIdentifier() {
    List<ApiInventoryDOI> bulkCreateResult =
        inventoryIdentifierApiMgr.registerBulkIdentifiers(1, user);
    assertThat(bulkCreateResult).hasSize(1);
    assertFalse(bulkCreateResult.get(0).isAssociated());
    assertNull(bulkCreateResult.get(0).getAssociatedGlobalId());
    assertNull(bulkCreateResult.get(0).getTitle());

    ApiContainer createdContainer = createBasicContainerForUser(user);
    assertThat(createdContainer.getIdentifiers()).isEmpty();

    ApiInventoryRecordInfo assignIdentifierResult =
        inventoryIdentifierApiMgr.assignIdentifier(
            createdContainer.getOid(), bulkCreateResult.get(0).getId(), user);
    assertThat(assignIdentifierResult.getIdentifiers()).hasSize(1);
    assertEquals(
        createdContainer.getOid().getIdString(),
        assignIdentifierResult.getIdentifiers().get(0).getAssociatedGlobalId());
    assertEquals(
        createdContainer.getName(), assignIdentifierResult.getIdentifiers().get(0).getTitle());

    Container refreshedContainer = containerApiMgr.getContainerById(createdContainer.getId(), user);
    assertThat(refreshedContainer.getActiveIdentifiers()).hasSize(1);
    assertEquals(
        bulkCreateResult.get(0).getDoi(),
        refreshedContainer.getActiveIdentifiers().get(0).getIdentifier());

    // cleanup identifiers
    ApiInventoryRecordInfo updatedContainer =
        inventoryIdentifierApiMgr.deleteAssociatedIdentifier(refreshedContainer.getOid(), user);
    assertThat(updatedContainer.getIdentifiers()).isEmpty();
  }

  @Test
  public void testAssignIdentifierToAnInventoryItemThatGotAlreadyAnIdentifierThrowsErrors() {
    ApiSampleWithFullSubSamples createdSample = createComplexSampleForUser(user);
    ApiInventoryRecordInfo registeredIdentifier =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdSample.getOid(), user);

    ApiInventoryDOI unassignedIdentifier =
        inventoryIdentifierApiMgr.registerBulkIdentifiers(1, user).get(0);

    boolean exceptionHappened = false;
    try {
      inventoryIdentifierApiMgr.assignIdentifier(
          createdSample.getOid(), unassignedIdentifier.getId(), user);
    } catch (IllegalArgumentException e) {
      exceptionHappened = true;
      assertEquals(
          "Inventory Item ["
              + registeredIdentifier.getOid().getIdString()
              + "] has got already an identifier",
          e.getMessage());
    } finally {
      assertTrue(exceptionHappened, "The exception didn't happen");
      // cleanup
      inventoryIdentifierApiMgr.deleteAssociatedIdentifier(createdSample.getOid(), user);
      inventoryIdentifierApiMgr.deleteUnassociatedIdentifier(unassignedIdentifier, user);
    }
  }

  @Test
  public void testAssignIdentifierThatWasAlreadyAssociatedThrowsErrors() {
    ApiSampleWithFullSubSamples createdSample = createComplexSampleForUser(user);
    ApiInventoryRecordInfo registeredIdentifier =
        inventoryIdentifierApiMgr.registerNewIdentifier(createdSample.getOid(), user);

    ApiContainer createdContainer = createBasicContainerForUser(user);
    assertThat(createdContainer.getIdentifiers()).isEmpty();

    boolean exceptionHappened = false;
    try {
      inventoryIdentifierApiMgr.assignIdentifier(
          createdContainer.getOid(), registeredIdentifier.getIdentifiers().get(0).getId(), user);
    } catch (IllegalArgumentException e) {
      exceptionHappened = true;
      assertEquals(
          "You can only assign an active unassigned identifier in \"draft\" state", e.getMessage());
    } finally {
      assertTrue(exceptionHappened, "The exception didn't happen");
      // cleanup
      inventoryIdentifierApiMgr.deleteAssociatedIdentifier(createdSample.getOid(), user);
    }
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

    assertThat(updatedSubSample.getIdentifiers()).hasSize(1);
    assertEquals("draft", updatedSubSample.getIdentifiers().get(0).getState());
    assertNotNull(updatedSubSample.getIdentifiers().get(0).getRsPublicId());
    assertNotNull(updatedSubSample.getIdentifiers().get(0).getUrl());
    assertNull(updatedSubSample.getIdentifiers().get(0).getPublicUrl());
    // publish
    updatedSubSample = inventoryIdentifierApiMgr.publishIdentifier(createdSubSample.getOid(), user);
    assertThat(updatedSubSample.getIdentifiers()).hasSize(1);
    assertNotNull(updatedSubSample.getIdentifiers().get(0).getUrl());
    assertThat(updatedSubSample.getIdentifiers().get(0).getPublicUrl())
        .startsWith("https://doi.org/" + DUMMY_VALID_DOI);
    // retract
    updatedSubSample = inventoryIdentifierApiMgr.retractIdentifier(createdSubSample.getOid(), user);
    assertThat(updatedSubSample.getIdentifiers()).hasSize(1);
    assertNotNull(updatedSubSample.getIdentifiers().get(0).getUrl());
    assertThat(updatedSubSample.getIdentifiers().get(0).getPublicUrl())
        .startsWith("https://doi.org/" + DUMMY_VALID_DOI);
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
    assertThat(dataCiteDoi.getAttributes().getSubjects()).hasSize(1);
    assertNotNull(dataCiteDoi.getAttributes().getDescriptions());
    assertThat(dataCiteDoi.getAttributes().getDescriptions()).hasSize(1);
    assertEquals("testDesc", dataCiteDoi.getAttributes().getDescriptions().get(0).getDescription());
    assertEquals(
        "Abstract", dataCiteDoi.getAttributes().getDescriptions().get(0).getDescriptionType());
    assertThat(dataCiteDoi.getAttributes().getDescriptions()).hasSize(1);
    assertNotNull(dataCiteDoi.getAttributes().getAlternateIdentifiers());
    assertThat(dataCiteDoi.getAttributes().getAlternateIdentifiers()).hasSize(1);
    assertNotNull(dataCiteDoi.getAttributes().getDates());
    assertThat(dataCiteDoi.getAttributes().getDates()).hasSize(1);
    assertNotNull(dataCiteDoi.getAttributes().getGeoLocations());
    assertThat(dataCiteDoi.getAttributes().getGeoLocations()).hasSize(2);
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
