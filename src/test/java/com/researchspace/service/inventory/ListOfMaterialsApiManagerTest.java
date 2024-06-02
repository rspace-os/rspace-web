package com.researchspace.service.inventory;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.events.ListOfMaterialsCreationEvent;
import com.researchspace.model.events.ListOfMaterialsDeleteEvent;
import com.researchspace.model.events.ListOfMaterialsEditingEvent;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.BindException;

public class ListOfMaterialsApiManagerTest extends SpringTransactionalTest {

  private @Autowired ListOfMaterialsApiManager lomManager;
  private @Autowired SubSampleApiManager subSampleManager;

  private ApplicationEventPublisher mockPublisher;
  private User testUser;

  @Before
  public void setUp() throws Exception {
    super.setUp();

    sampleDao.resetDefaultTemplateOwner();
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(testUser);
    assertTrue(testUser.isContentInitialized());

    mockPublisher = Mockito.mock(ApplicationEventPublisher.class);
    lomManager.setPublisher(mockPublisher);
  }

  @Test
  public void testBasicLomOperations() throws BindException {

    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(testUser, "test");
    Long basicDocFieldId = basicDoc.getFields().get(0).getId();

    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    ApiSubSample basicSubSample = basicSample.getSubSamples().get(0);
    GlobalIdentifier basicSubSampleOid = new GlobalIdentifier(basicSubSample.getGlobalId());

    List<ApiListOfMaterials> foundLoms =
        lomManager.getListOfMaterialsForFieldId(basicDocFieldId, testUser);
    assertEquals(0, foundLoms.size());
    foundLoms = lomManager.getListOfMaterialsForInvRecGlobalId(basicSubSampleOid, testUser);
    assertEquals(0, foundLoms.size());

    // add new lom
    ApiListOfMaterials newLom = new ApiListOfMaterials();
    newLom.setName("test LoM");
    newLom.setElnFieldId(basicDocFieldId);
    newLom.addMaterialUsage(
        basicSubSample, basicSubSample.getQuantity(), false); // add subsample usage
    ApiListOfMaterials createdLom = lomManager.createNewListOfMaterials(newLom, testUser);
    assertNotNull(createdLom.getId());
    assertEquals(1, createdLom.getMaterials().size());
    ApiMaterialUsage createdSubSampleUsage = createdLom.getMaterials().get(0);
    assertEquals("mySubSample", createdSubSampleUsage.getRecord().getName());
    assertEquals("5 g", createdSubSampleUsage.getUsedQuantity().toQuantityInfo().toPlainString());
    assertEquals(3, createdSubSampleUsage.getRecord().getPermittedActions().size());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(ListOfMaterialsCreationEvent.class));

    // check lom can be found when querying for field loms or by id
    foundLoms = lomManager.getListOfMaterialsForFieldId(basicDocFieldId, testUser);
    assertEquals(1, foundLoms.size());
    assertNull(
        foundLoms.get(0).getElnDocument()); // not populated when searching with doc/field ids
    foundLoms = lomManager.getListOfMaterialsForInvRecGlobalId(basicSubSampleOid, testUser);
    assertEquals(1, foundLoms.size());
    assertNotNull(
        foundLoms.get(0).getElnDocument()); // not populated when searching with doc/field ids

    ApiListOfMaterials secondLom = lomManager.getListOfMaterialsById(createdLom.getId(), testUser);
    assertEquals(createdLom, secondLom);

    // update
    ApiListOfMaterials lomUpdate = new ApiListOfMaterials();
    Long secondLomId = secondLom.getId();
    lomUpdate.setId(secondLomId);
    lomUpdate.setName("updatedName");
    lomUpdate.setDescription("new description");
    lomUpdate.addMaterialUsage(basicSample, null, false); // switch to sample usage
    lomManager.updateListOfMaterials(lomUpdate, testUser);

    // verify updated fine
    secondLom = lomManager.getListOfMaterialsById(secondLomId, testUser);
    assertEquals("updatedName", secondLom.getName());
    assertEquals(1, secondLom.getMaterials().size());
    assertEquals("mySample", secondLom.getMaterials().get(0).getRecord().getName());
    assertEquals(3, secondLom.getMaterials().get(0).getRecord().getPermittedActions().size());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(ListOfMaterialsEditingEvent.class));

    // delete
    lomManager.deleteListOfMaterials(secondLomId, testUser);
    NotFoundException nfe =
        assertThrows(
            NotFoundException.class,
            () -> lomManager.getListOfMaterialsById(secondLomId, testUser));
    assertTrue(nfe.getMessage().contains("possibly it has been deleted"), nfe.getMessage());
    foundLoms = lomManager.getListOfMaterialsForFieldId(basicDocFieldId, testUser);
    assertEquals(0, foundLoms.size());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(ListOfMaterialsDeleteEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void testMaterialUsageChangesWithinLom() throws BindException {

    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(testUser, "test");
    Field basicDocField = basicDoc.getFields().get(0);

    // create LoM with empty list of materials
    ApiListOfMaterials createdLom =
        createBasicListOfMaterialsForUserAndDocField(testUser, basicDocField, null);
    Long createdLomId = createdLom.getId();
    ApiListOfMaterials latestLom = lomManager.getListOfMaterialsById(createdLomId, testUser);
    assertEquals(0, latestLom.getMaterials().size());

    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    ApiSubSample basicSubSample = basicSample.getSubSamples().get(0);
    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(testUser);
    ApiContainer basicContainer = createBasicContainerForUser(testUser);

    // update to store usage of basic subsample
    ApiListOfMaterials lomUpdate = new ApiListOfMaterials();
    lomUpdate.setId(createdLomId);
    lomUpdate.addMaterialUsage(basicSubSample, basicSubSample.getQuantity(), false);
    ApiListOfMaterials updatedLom = lomManager.updateListOfMaterials(lomUpdate, testUser);

    latestLom = lomManager.getListOfMaterialsById(createdLomId, testUser);
    assertEquals(updatedLom, latestLom);
    assertEquals(1, latestLom.getMaterials().size());
    assertEquals("mySubSample", latestLom.getMaterials().get(0).getRecord().getName());
    assertEquals(
        "5 g", latestLom.getMaterials().get(0).getUsedQuantity().toQuantityInfo().toPlainString());
    // inventory quantity not updated
    assertEquals(
        "5 g",
        latestLom.getMaterials().get(0).getRecord().getQuantity().toQuantityInfo().toPlainString());

    // update to store different usage
    lomUpdate
        .getMaterials()
        .get(0)
        .setUsedQuantity(new ApiQuantityInfo(BigDecimal.valueOf(3.5), RSUnitDef.GRAM));
    updatedLom = lomManager.updateListOfMaterials(lomUpdate, testUser);

    latestLom = lomManager.getListOfMaterialsById(createdLomId, testUser);
    assertEquals(updatedLom, latestLom);
    assertEquals(1, latestLom.getMaterials().size());
    assertEquals("mySubSample", latestLom.getMaterials().get(0).getRecord().getName());
    assertEquals(
        "3.5 g",
        latestLom.getMaterials().get(0).getUsedQuantity().toQuantityInfo().toPlainString());
    // inventory quantity not updated
    assertEquals(
        "5 g",
        latestLom.getMaterials().get(0).getRecord().getQuantity().toQuantityInfo().toPlainString());

    // change to add usage of complex sample, and a container
    lomUpdate.addMaterialUsage(complexSample, null, false);
    lomUpdate.addMaterialUsage(basicContainer, null, false);
    updatedLom = lomManager.updateListOfMaterials(lomUpdate, testUser);

    latestLom = lomManager.getListOfMaterialsById(createdLomId, testUser);
    assertEquals(updatedLom, latestLom);
    assertEquals(3, latestLom.getMaterials().size());
    assertEquals("mySubSample", latestLom.getMaterials().get(0).getRecord().getName());
    assertEquals("myComplexSample", latestLom.getMaterials().get(1).getRecord().getName());
    assertEquals("listContainer", latestLom.getMaterials().get(2).getRecord().getName());
  }

  @Test
  public void testLomUsageChangesWithUpdatingRemaining() throws BindException {

    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(testUser, "test");
    Field basicDocField = basicDoc.getFields().get(0);

    // create LoM with empty list of materials
    ApiListOfMaterials createdLom =
        createBasicListOfMaterialsForUserAndDocField(testUser, basicDocField, null);
    Long createdLomId = createdLom.getId();

    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    ApiSubSample basicSubSample = basicSample.getSubSamples().get(0);

    Long subSampleId = basicSubSample.getId();
    ApiSubSample latestSubSample = subSampleManager.getApiSubSampleById(subSampleId, testUser);
    assertEquals("5 g", latestSubSample.getQuantity().toQuantityInfo().toPlainString());

    // update to store usage of basic subsample
    ApiListOfMaterials lomUpdate = new ApiListOfMaterials();
    lomUpdate.setId(createdLomId);
    lomUpdate.addMaterialUsage(basicSubSample, basicSubSample.getQuantity(), true);
    ApiListOfMaterials updatedLom = lomManager.updateListOfMaterials(lomUpdate, testUser);

    ApiListOfMaterials latestLom = lomManager.getListOfMaterialsById(createdLomId, testUser);
    assertEquals(updatedLom, latestLom);
    assertEquals(1, latestLom.getMaterials().size());
    assertEquals("mySubSample", latestLom.getMaterials().get(0).getRecord().getName());
    assertEquals(
        "5 g", latestLom.getMaterials().get(0).getUsedQuantity().toQuantityInfo().toPlainString());
    assertEquals(
        "0 g",
        latestLom.getMaterials().get(0).getRecord().getQuantity().toQuantityInfo().toPlainString());

    latestSubSample = subSampleManager.getApiSubSampleById(subSampleId, testUser);
    assertEquals("0 g", latestSubSample.getQuantity().toQuantityInfo().toPlainString());

    // reduce the used quantity
    lomUpdate
        .getMaterials()
        .get(0)
        .setUsedQuantity(new ApiQuantityInfo(BigDecimal.valueOf(3.5), RSUnitDef.GRAM));
    updatedLom = lomManager.updateListOfMaterials(lomUpdate, testUser);
    assertEquals(
        "3.5 g",
        updatedLom.getMaterials().get(0).getUsedQuantity().toQuantityInfo().toPlainString());
    assertEquals(
        "1.5 g",
        updatedLom
            .getMaterials()
            .get(0)
            .getRecord()
            .getQuantity()
            .toQuantityInfo()
            .toPlainString());

    latestSubSample = subSampleManager.getApiSubSampleById(subSampleId, testUser);
    assertEquals("1.5 g", latestSubSample.getQuantity().toQuantityInfo().toPlainString());

    // increase the used quantity
    lomUpdate
        .getMaterials()
        .get(0)
        .setUsedQuantity(new ApiQuantityInfo(BigDecimal.valueOf(3.8), RSUnitDef.GRAM));
    updatedLom = lomManager.updateListOfMaterials(lomUpdate, testUser);
    assertEquals(
        "3.8 g",
        updatedLom.getMaterials().get(0).getUsedQuantity().toQuantityInfo().toPlainString());
    assertEquals(
        "1.2 g",
        updatedLom
            .getMaterials()
            .get(0)
            .getRecord()
            .getQuantity()
            .toQuantityInfo()
            .toPlainString());

    latestSubSample = subSampleManager.getApiSubSampleById(subSampleId, testUser);
    assertEquals("1.2 g", latestSubSample.getQuantity().toQuantityInfo().toPlainString());

    // reduce above the inventory stock - should zero the inventory quantity.
    lomUpdate
        .getMaterials()
        .get(0)
        .setUsedQuantity(new ApiQuantityInfo(BigDecimal.valueOf(7.5), RSUnitDef.GRAM));
    updatedLom = lomManager.updateListOfMaterials(lomUpdate, testUser);
    assertEquals(
        "7.5 g",
        updatedLom.getMaterials().get(0).getUsedQuantity().toQuantityInfo().toPlainString());
    assertEquals(
        "0 g",
        updatedLom
            .getMaterials()
            .get(0)
            .getRecord()
            .getQuantity()
            .toQuantityInfo()
            .toPlainString());

    latestSubSample = subSampleManager.getApiSubSampleById(subSampleId, testUser);
    assertEquals("0 g", latestSubSample.getQuantity().toQuantityInfo().toPlainString());
  }

  @Test
  public void deletedItemAllowedInLom() throws BindException {

    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(testUser, "test");
    Long basicDocFieldId = basicDoc.getFields().get(0).getId();

    // create a sample and delete its subsample
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    ApiSubSample basicSubSample = basicSample.getSubSamples().get(0);
    subSampleApiMgr.markSubSampleAsDeleted(basicSubSample.getId(), testUser, false);

    // add new lom with sample and deleted subsample
    ApiListOfMaterials newLom = new ApiListOfMaterials();
    newLom.setName("new lom");
    newLom.setElnFieldId(basicDocFieldId);
    newLom.addMaterialUsage(basicSample, null, false); // add subsample usage
    newLom.addMaterialUsage(
        basicSubSample, basicSubSample.getQuantity(), false); // add subsample usage
    ApiListOfMaterials createdLom = lomManager.createNewListOfMaterials(newLom, testUser);
    assertNotNull(createdLom.getId());
    assertEquals(2, createdLom.getMaterials().size());
    assertEquals("mySample", createdLom.getMaterials().get(0).getRecord().getName());
    assertEquals("mySubSample", createdLom.getMaterials().get(1).getRecord().getName());

    // retrieve lom by doc id
    GlobalIdentifier basicSampleOid = new GlobalIdentifier(basicSample.getGlobalId());
    List<ApiListOfMaterials> foundLoms =
        lomManager.getListOfMaterialsForDocId(basicDoc.getId(), testUser);
    assertEquals(1, foundLoms.size());
    assertEquals(2, foundLoms.get(0).getMaterials().size());

    // check lom can be found when querying for deleted item's id (PRT-605)
    GlobalIdentifier basicSubSampleOid = new GlobalIdentifier(basicSubSample.getGlobalId());
    foundLoms = lomManager.getListOfMaterialsForInvRecGlobalId(basicSubSampleOid, testUser);
    assertEquals(1, foundLoms.size());
    assertEquals(2, foundLoms.get(0).getMaterials().size());
  }

  @Test
  public void onlyItemsReadableByUserCanBeAddedToLom() {

    // create a pi and a group
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(piUser, otherUser);

    // groupA with pi and test user
    Group groupA = createGroup("groupA", piUser);
    addUsersToGroup(piUser, groupA, testUser);

    // as a pi create 2 private containers, one within another
    ApiContainer piContainer = createBasicContainerForUser(piUser, "c1", List.of());
    ApiContainer piSubContainer = createBasicContainerForUser(piUser, "c2", List.of());
    moveContainerIntoListContainer(piSubContainer.getId(), piContainer.getId(), piUser);

    // sanity-check that testUser has limited-read access to container, but not to subcontainer
    assertTrue(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(piContainer.getOid(), testUser));
    assertFalse(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            piSubContainer.getOid(), testUser));

    // as a testUser try creating a private document with a lom listing pi's subcontainer
    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(testUser, "test");
    Long basicDocFieldId = basicDoc.getFields().get(0).getId();

    ApiListOfMaterials newLom = new ApiListOfMaterials();
    newLom.setName("new lom");
    newLom.setElnFieldId(basicDocFieldId);
    newLom.addMaterialUsage(piSubContainer, null, false);
    NotFoundException nfe =
        assertThrows(
            NotFoundException.class, () -> lomManager.createNewListOfMaterials(newLom, testUser));
    assertTrue(
        nfe.getMessage().contains("or you do not have permission to access it"), nfe.getMessage());

    // sanity-check: testUser can add LoM with limited-read container fine
    newLom.getMaterials().clear();
    newLom.addMaterialUsage(piContainer, null, false);
    ApiListOfMaterials createdLom = lomManager.createNewListOfMaterials(newLom, testUser);
    assertNotNull(createdLom.getId());
    assertEquals(1, createdLom.getMaterials().size());

    // user sholdn't be able to update the lom with subcontainer either
    ApiListOfMaterials lomUpdate = new ApiListOfMaterials();
    Long secondLomId = createdLom.getId();
    lomUpdate.setId(secondLomId);
    lomUpdate.addMaterialUsage(piSubContainer, null, false); // switch to subcontainer usage
    nfe =
        assertThrows(
            NotFoundException.class, () -> lomManager.updateListOfMaterials(lomUpdate, testUser));
    assertTrue(
        nfe.getMessage().contains("or you do not have permission to access it"), nfe.getMessage());
  }

  @Test
  public void accessToLomGivesLimitedViewToItsItems() {

    // create a pi and a group
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(piUser, otherUser);

    // groupA with pi and test user
    Group groupA = createGroup("groupA", piUser);
    addUsersToGroup(piUser, groupA, testUser);

    // as a pi create 2 private containers and 2 private samples
    ApiContainer apiContainer = createBasicContainerForUser(piUser, "c1", List.of());
    ApiContainer apiSubContainer = createBasicContainerForUser(piUser, "c2", List.of());
    ApiSampleWithFullSubSamples createdSample1 =
        createBasicSampleForUser(piUser, "s1", "ss1", List.of());
    ApiSubSample createdSubSample1 = createdSample1.getSubSamples().get(0);
    ApiSampleWithFullSubSamples createdSample2 =
        createBasicSampleForUser(piUser, "s2", "ss2", List.of());
    ApiSubSample createdSubSample2 = createdSample2.getSubSamples().get(0);

    // move subscontainer and subsamples into private container, making them effectively
    // non-readable by other group members
    moveContainerIntoListContainer(apiSubContainer.getId(), apiContainer.getId(), piUser);
    moveSubSampleIntoListContainer(createdSubSample1.getId(), apiContainer.getId(), piUser);
    moveSubSampleIntoListContainer(createdSubSample2.getId(), apiContainer.getId(), piUser);

    // as a pi create a private document with a lom listing a container, sample and subsample
    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(piUser, "test");
    Long basicDocFieldId = basicDoc.getFields().get(0).getId();

    ApiListOfMaterials newLom = new ApiListOfMaterials();
    newLom.setName("new lom");
    newLom.setElnFieldId(basicDocFieldId);
    newLom.addMaterialUsage(apiSubContainer, null, false); // add container usage
    newLom.addMaterialUsage(createdSample1, null, false); // add sample usage
    newLom.addMaterialUsage(
        createdSubSample2, createdSubSample1.getQuantity(), false); // add subsample usage
    ApiListOfMaterials createdLom = lomManager.createNewListOfMaterials(newLom, piUser);
    assertNotNull(createdLom.getId());
    assertEquals(3, createdLom.getMaterials().size());

    // sanity-check that lom and items on it are non-readeable by testUser
    assertFalse(lomManager.canUserAccessApiLom(basicDocFieldId, testUser, PermissionType.READ));
    assertFalse(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            apiSubContainer.getOid(), testUser));
    assertFalse(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            createdSample1.getOid(), testUser));
    assertFalse(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            createdSubSample2.getOid(), testUser));

    // as a pi share the document with group
    logoutAndLoginAs(piUser);
    shareRecordWithGroup(piUser, groupA, basicDoc);

    // testUser should now have limited view permission to the items
    assertTrue(lomManager.canUserAccessApiLom(basicDocFieldId, testUser, PermissionType.READ));
    assertTrue(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            apiSubContainer.getOid(), testUser));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(apiSubContainer.getOid(), testUser));
    assertTrue(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            createdSample1.getOid(), testUser));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(createdSample1.getOid(), testUser));
    assertTrue(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            createdSubSample2.getOid(), testUser));
    assertFalse(
        invPermissionUtils.canUserReadInventoryRecord(createdSubSample2.getOid(), testUser));

    // should be able to retrieve lom by id id
    ApiListOfMaterials foundLom = lomManager.getListOfMaterialsById(createdLom.getId(), testUser);
    assertEquals(3, foundLom.getMaterials().size());
    // items should have permittedAction populated to 'LIMITED_READ'
    ApiInventoryRecordInfo containerOnLomListing = foundLom.getMaterials().get(0).getRecord();
    assertEquals(
        apiSubContainer.getGlobalId(),
        containerOnLomListing.getGlobalId()); // not visible in limited view
    assertEquals(1, containerOnLomListing.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.LIMITED_READ,
        containerOnLomListing.getPermittedActions().get(0));
    assertNull(containerOnLomListing.getModifiedBy()); // not visible in limited view

    ApiInventoryRecordInfo sampleOnLomListing = foundLom.getMaterials().get(1).getRecord();
    assertEquals(
        createdSample1.getGlobalId(),
        sampleOnLomListing.getGlobalId()); // not visible in limited view
    assertEquals(1, sampleOnLomListing.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.LIMITED_READ,
        sampleOnLomListing.getPermittedActions().get(0));
    assertNull(sampleOnLomListing.getModifiedBy()); // not visible in limited view

    ApiInventoryRecordInfo subSampleOnLomListing = foundLom.getMaterials().get(2).getRecord();
    assertEquals(
        createdSubSample2.getGlobalId(),
        subSampleOnLomListing.getGlobalId()); // not visible in limited view
    assertEquals(1, subSampleOnLomListing.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.LIMITED_READ,
        subSampleOnLomListing.getPermittedActions().get(0));
    assertNull(subSampleOnLomListing.getModifiedBy()); // not visible in limited view
  }
}
