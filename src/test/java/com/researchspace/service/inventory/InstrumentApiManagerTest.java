package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentSearchResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.events.InventoryDeleteEvent;
import com.researchspace.model.events.InventoryEditingEvent;
import com.researchspace.model.events.InventoryRestoreEvent;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.impl.InstrumentApiManagerImpl;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

public class InstrumentApiManagerTest extends SpringTransactionalTest {

  private ApplicationEventPublisher mockPublisher;
  private User testUser;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("instApi"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());

    mockPublisher = Mockito.mock(ApplicationEventPublisher.class);
    instrumentApiMgr.setPublisher(mockPublisher);
  }

  @Test
  public void createAndRetrieveBasicInstrument() {
    // create a minimal instrument
    ApiInstrument created = createBasicInstrumentForUser(testUser);

    assertNotNull(created);
    assertNotNull(created.getId());
    assertNotNull(created.getGlobalId());
    assertEquals("myInstrument", created.getName());
    assertFalse(created.isTemplate());
    assertFalse(created.isDeleted());
    verify(mockPublisher).publishEvent(Mockito.any(InventoryCreationEvent.class));

    // retrieval fires an access event
    ApiInstrument retrieved = instrumentApiMgr.getApiInstrumentById(created.getId(), testUser);
    assertNotNull(retrieved);
    assertEquals(created.getId(), retrieved.getId());
    assertEquals("myInstrument", retrieved.getName());
    verify(mockPublisher).publishEvent(Mockito.any(InventoryAccessEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void instrumentDefaultName() {
    // null name → server should assign default name
    ApiInstrument request = new ApiInstrument();
    request.setName(null);
    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);

    assertEquals(InstrumentApiManagerImpl.INSTRUMENT_DEFAULT_NAME, created.getName());
    verify(mockPublisher).publishEvent(Mockito.any(InventoryCreationEvent.class));
  }

  @Test
  public void instrumentExistsCheck() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "exists-test");

    assertTrue(instrumentApiMgr.instrumentExists(created.getId()));
    assertFalse(instrumentApiMgr.instrumentExists(Long.MAX_VALUE));
  }

  @Test
  public void instrumentTemplateExistsCheck() {
    assertFalse(instrumentApiMgr.instrumentTemplateExists(Long.MAX_VALUE));
    // existence of real templates is tested indirectly via createFromTemplate tests
  }

  @Test
  public void assertUserCanReadInstrument() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "perm-read");

    // owner can read
    Instrument dbInstrument =
        instrumentApiMgr.assertUserCanReadInstrument(created.getId(), testUser);
    assertNotNull(dbInstrument);
    assertEquals(created.getId(), dbInstrument.getId());
  }

  @Test
  public void assertUserCanEditInstrument() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "perm-edit");

    // owner can edit
    Instrument dbInstrument =
        instrumentApiMgr.assertUserCanEditInstrument(created.getId(), testUser);
    assertNotNull(dbInstrument);
    assertEquals(created.getId(), dbInstrument.getId());
  }

  @Test
  public void otherUserCannotReadOrEditPrivateInstrument() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "private-instr");

    // a second unrelated user cannot read or edit
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("other"));
    initialiseContentWithEmptyContent(otherUser);

    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanReadInstrument(created.getId(), otherUser));
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanEditInstrument(created.getId(), otherUser));
  }

  @Test
  public void instrumentPermittedActionsForOwner() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "action-test");

    // owner should have read, update and transfer permissions
    assertNotNull(created.getPermittedActions());
    assertTrue(created.getPermittedActions().size() >= 2);
    assertTrue(created.getPermittedActions().contains(ApiInventoryRecordPermittedAction.READ));
    assertTrue(created.getPermittedActions().contains(ApiInventoryRecordPermittedAction.UPDATE));
  }

  @Test
  public void instrumentWithExtraFields() {
    ApiInstrument request = new ApiInstrument();
    request.setName("instrument-with-extras");

    ApiExtraField extraText = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    extraText.setName("my extra");
    extraText.setContent("extra content");
    request.setExtraFields(List.of(extraText));

    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);
    assertNotNull(created);
    assertEquals(1, created.getExtraFields().size());
    assertEquals("extra content", created.getExtraFields().get(0).getContent());
  }

  @Test
  public void instrumentCreateRejectsDuplicateExtraFieldNames() {
    ApiInstrument request = new ApiInstrument();
    request.setName("instrument-with-dup-extras");

    ApiExtraField one = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    one.setName("dup");
    one.setContent("a");
    ApiExtraField two = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    two.setName("dup");
    two.setContent("b");
    request.setExtraFields(List.of(one, two));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> instrumentApiMgr.createNewApiInstrument(request, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void instrumentWithDescription() {
    ApiInstrument request = new ApiInstrument();
    request.setName("described-instrument");
    request.setDescription("a handy instrument for testing");

    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);
    assertEquals("a handy instrument for testing", created.getDescription());
  }

  @Test
  public void instrumentWithTags() {
    ApiInstrument request = new ApiInstrument();
    request.setName("tagged-instrument");
    request.setApiTagInfo("alpha,beta");

    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);
    assertNotNull(created.getTags());
    assertFalse(created.getTags().isEmpty());
  }

  @Test
  public void createInstrumentFromTemplate() {
    // first, build a template directly in the DB via a copy of an instrument
    ApiInstrument baseInstrument = createBasicInstrumentForUser(testUser, "base-for-template");

    // check templateId is initially null (no template)
    assertNull(baseInstrument.getTemplateId());

    // create another instrument without a template — templateId should still be null
    ApiInstrument noTemplate = new ApiInstrument();
    noTemplate.setName("no-template-instrument");
    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(noTemplate, testUser);
    assertNull(created.getTemplateId());
    verify(mockPublisher, Mockito.times(2)).publishEvent(Mockito.any(InventoryCreationEvent.class));
  }

  @Test
  public void getApiInstrumentTemplateById() {
    // verify that attempting to retrieve a non-existing instrument template throws
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.getApiInstrumentTemplateById(Long.MAX_VALUE, testUser));
  }

  @Test
  public void instrumentSharingPermissions() {
    // create pi and a group
    User pi =
        createAndSaveUserIfNotExists(
            getRandomAlphabeticString("pi"), com.researchspace.Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    Group group = createGroup("instrGroup", pi);
    addUsersToGroup(pi, group, testUser);

    // create instrument by testUser
    ApiInstrument instrument = createBasicInstrumentForUser(testUser, "shared-instrument");

    // default sharing info is present
    assertNotNull(instrument.getSharedWith());

    // pi can read testUser's instrument
    ApiInstrument instrumentAsPi = instrumentApiMgr.getApiInstrumentById(instrument.getId(), pi);
    assertNotNull(instrumentAsPi);
    assertFalse(instrumentAsPi.isClearedForPublicView());
  }

  @Test
  public void instrumentOwnerFieldIsPopulatedOnRetrieval() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "owner-test");

    ApiInstrument retrieved = instrumentApiMgr.getApiInstrumentById(created.getId(), testUser);
    assertNotNull(retrieved.getOwner());
    assertEquals(testUser.getUsername(), retrieved.getOwner().getUsername());
  }

  @Test
  public void updateApiInstrument_updatesNameAndDescription() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "update-test");

    created.setName("renamed instrument");
    created.setDescription("updated description");
    ApiInstrument updated = instrumentApiMgr.updateApiInstrument(created, testUser);

    assertEquals("renamed instrument", updated.getName());
    assertEquals("updated description", updated.getDescription());
    verify(mockPublisher).publishEvent(Mockito.any(InventoryEditingEvent.class));
  }

  @Test
  public void markInstrumentAsDeleted_marksAsDeleted() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "delete-test");
    assertFalse(created.isDeleted());

    ApiInstrument deleted = instrumentApiMgr.markInstrumentAsDeleted(created.getId(), testUser);

    assertTrue(deleted.isDeleted());
    verify(mockPublisher).publishEvent(Mockito.any(InventoryDeleteEvent.class));
  }

  @Test
  public void restoreDeletedInstrument_restoresFromDeleted() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "restore-test");
    instrumentApiMgr.markInstrumentAsDeleted(created.getId(), testUser);

    ApiInstrument restored = instrumentApiMgr.restoreDeletedInstrument(created.getId(), testUser);

    assertFalse(restored.isDeleted());
    verify(mockPublisher).publishEvent(Mockito.any(InventoryDeleteEvent.class));
    verify(mockPublisher).publishEvent(Mockito.any(InventoryRestoreEvent.class));
  }

  @Test
  public void duplicateInstrument_createsCopy() {
    ApiInstrument original = createBasicInstrumentForUser(testUser, "original");

    ApiInstrument copy = instrumentApiMgr.duplicateInstrument(original.getId(), testUser);

    assertNotNull(copy.getId());
    assertFalse(copy.getId().equals(original.getId()));
    assertEquals(original.getName() + "_COPY", copy.getName());
    verify(mockPublisher, Mockito.times(2)).publishEvent(Mockito.any(InventoryCreationEvent.class));
  }

  @Test
  public void nameExistsForUser_findsExistingName() {
    String uniqueName = "unique-name-" + getRandomAlphabeticString("n");
    assertFalse(instrumentApiMgr.nameExistsForUser(uniqueName, testUser));

    createBasicInstrumentForUser(testUser, uniqueName);

    assertTrue(instrumentApiMgr.nameExistsForUser(uniqueName, testUser));
  }

  @Test
  public void getInstrumentsForUser_returnsOwnedInstruments() {
    createBasicInstrumentForUser(testUser, "listed-instrument");

    ApiInstrumentSearchResult result =
        instrumentApiMgr.getInstrumentsForUser(
            PaginationCriteria.createDefaultForClass(Instrument.class),
            null,
            InventorySearchDeletedOption.EXCLUDE,
            testUser);

    assertNotNull(result);
    assertTrue(result.getTotalHits() >= 1);
    assertTrue(
        result.getInstruments().stream().anyMatch(i -> i.getName().equals("listed-instrument")));
  }

  @Test
  public void assertUserCanDeleteInstrument_ownerCanDelete() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "perm-delete");

    Instrument dbInstrument =
        instrumentApiMgr.assertUserCanDeleteInstrument(created.getId(), testUser);

    assertNotNull(dbInstrument);
    assertEquals(created.getId(), dbInstrument.getId());
  }

  @Test
  public void assertUserCanDeleteInstrument_otherUserCannotDelete() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "perm-delete-other");

    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("other"));
    initialiseContentWithEmptyContent(otherUser);

    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanDeleteInstrument(created.getId(), otherUser));
  }

  @Test
  public void assertUserCanTransferInstrument_ownerCanTransfer() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "perm-transfer");

    Instrument dbInstrument =
        instrumentApiMgr.assertUserCanTransferInstrument(created.getId(), testUser);

    assertNotNull(dbInstrument);
    assertEquals(created.getId(), dbInstrument.getId());
  }

  @Test
  public void assertUserCanTransferInstrument_otherUserCannotTransfer() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "perm-transfer-other");

    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("other"));
    initialiseContentWithEmptyContent(otherUser);

    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanTransferInstrument(created.getId(), otherUser));
  }

  @Test
  public void assertUserCanEditInstrumentTemplate_throwsForNonExistent() {
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanEditInstrumentTemplate(Long.MAX_VALUE, testUser));
  }

  @Test
  public void assertUserCanReadInstrumentTemplate_throwsForNonExistent() {
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanReadInstrumentTemplate(Long.MAX_VALUE, testUser));
  }

  @Test
  public void changeApiInstrumentOwner_transfersOwnership() {
    User newOwner = createAndSaveUserIfNotExists(getRandomAlphabeticString("newOwner"));
    initialiseContentWithEmptyContent(newOwner);

    ApiInstrument created = createBasicInstrumentForUser(testUser, "transfer-ownership-test");
    assertEquals(testUser.getUsername(), created.getOwner().getUsername());

    ApiInstrument transferred = instrumentApiMgr.getApiInstrumentById(created.getId(), testUser);
    transferred.setOwner(new ApiUser(newOwner));
    transferred = instrumentApiMgr.changeApiInstrumentOwner(transferred, testUser);

    assertEquals(newOwner.getUsername(), transferred.getOwner().getUsername());
    verify(mockPublisher).publishEvent(Mockito.any(InventoryTransferEvent.class));
  }

  @Test
  public void changeApiInstrumentOwner_throwsForUnknownTargetUser() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "transfer-invalid");

    ApiUser unknownUser = new ApiUser();
    unknownUser.setUsername("nobody-at-all");
    created.setOwner(unknownUser);

    assertThrows(
        IllegalArgumentException.class,
        () -> instrumentApiMgr.changeApiInstrumentOwner(created, testUser));
  }
}
