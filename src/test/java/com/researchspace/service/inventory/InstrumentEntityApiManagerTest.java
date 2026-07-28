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
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentSearchResult;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInstrumentTemplateSearchResult;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.events.InventoryDeleteEvent;
import com.researchspace.model.events.InventoryEditingEvent;
import com.researchspace.model.events.InventoryMoveEvent;
import com.researchspace.model.events.InventoryRestoreEvent;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.service.inventory.impl.InstrumentEntityApiManagerImpl;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

public class InstrumentEntityApiManagerTest extends SpringTransactionalTest {

  /**
   * Minimal valid PNG as data URI, used for base64-image update tests. Kept as a constant here to
   * avoid a cross-layer dependency on the MVC test base.
   */
  private static final String BASE_64_PNG =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAQIAAAESAQMAAAAsV"
          + " 0mIAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAGUExURf///wAAAFXC034AAAAJcEhZcwAADsMAAA7"
          + " DAcdvqGQAAABWSURBVGje7dUhDsAgEETR5VYcv8fCgUEg1rXdhOR9/ZKRE5LO2kwbH4snHe8EQRAEQWxR88g+m"
          + " yAIgiDeCo9MEARBEP+Lmr/1yARBEARxhyj5fSktYgFPS1k85Tqe JQAAAABJRU5ErkJggg==";

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

    assertEquals(InstrumentEntityApiManagerImpl.INSTRUMENT_DEFAULT_NAME, created.getName());
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
  public void createInstrumentFromTemplateCopiesFieldsAndLinksTemplate() {
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(testUser);
    assertTrue(template.isTemplate());
    assertEquals(1, template.getFields().size());

    ApiInstrument request = new ApiInstrument();
    request.setName("from-template");
    request.setTemplateId(template.getId());

    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);

    assertNotNull(created);
    assertEquals(template.getId(), created.getTemplateId());
    assertEquals(template.getVersion(), created.getTemplateVersion());
    assertFalse(created.isTemplate());
    // fields copied from the template (1 in basic template)
    assertEquals(1, created.getFields().size());
    assertEquals(template.getFields().get(0).getName(), created.getFields().get(0).getName());
  }

  @Test
  public void createInstrumentWithoutTemplateLeavesTemplateIdNull() {
    ApiInstrument request = new ApiInstrument();
    request.setName("no-template-instrument");

    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);

    assertNull(created.getTemplateId());
    assertNull(created.getTemplateVersion());
  }

  @Test
  public void getApiInstrumentTemplateByIdThrowsForUnknownId() {
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.getApiInstrumentTemplateById(Long.MAX_VALUE, testUser));
  }

  @Test
  public void createInstrumentTemplate_persistsAndReturnsApi() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("created template");
    templatePost.getFields().add(createBasicApiSampleField("F1", ApiFieldType.TEXT, "default"));

    ApiInstrumentTemplate created =
        instrumentApiMgr.createInstrumentTemplate(templatePost, testUser);

    assertNotNull(created);
    assertNotNull(created.getId());
    assertTrue(created.isTemplate());
    assertEquals("created template", created.getName());
    assertEquals(1L, (long) created.getVersion());
    assertEquals(1, created.getFields().size());
    verify(mockPublisher).publishEvent(Mockito.any(InventoryCreationEvent.class));
    assertTrue(instrumentApiMgr.instrumentTemplateExists(created.getId()));

    ApiInstrumentTemplate retrieved =
        instrumentApiMgr.getApiInstrumentTemplateById(created.getId(), testUser);
    assertEquals(created.getId(), retrieved.getId());
    assertEquals("created template", retrieved.getName());
  }

  @Test
  public void getTemplatesForUser_returnsOnlyTemplates() {
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(testUser);
    createBasicInstrumentForUser(testUser, "instance-not-template");

    ApiInstrumentTemplateSearchResult result =
        instrumentApiMgr.getTemplatesForUser(
            PaginationCriteria.createDefaultForClass(InstrumentTemplate.class),
            null,
            InventorySearchDeletedOption.EXCLUDE,
            testUser);

    assertNotNull(result);
    assertTrue(result.getTotalHits() >= 1);
    assertTrue(result.getTemplates().stream().allMatch(t -> t.isTemplate()));
    assertTrue(result.getTemplates().stream().anyMatch(t -> t.getId().equals(template.getId())));
  }

  @Test
  public void updateApiInstrumentTemplate_bumpsVersionOnContentChange() {
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(testUser);
    long initialVersion = template.getVersion();

    template.setName("renamed template");
    ApiInstrumentTemplate updated =
        instrumentApiMgr.updateApiInstrumentTemplate(template, testUser);

    assertEquals("renamed template", updated.getName());
    assertEquals(initialVersion + 1, (long) updated.getVersion());
    verify(mockPublisher).publishEvent(Mockito.any(InventoryEditingEvent.class));
  }

  @Test
  public void updateApiInstrumentTemplate_canAddAndDeleteFields() {
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(testUser);
    Long existingFieldId = template.getFields().get(0).getId();

    ApiInventoryEntityField newField = new ApiInventoryEntityField();
    newField.setName("added");
    newField.setType(ApiFieldType.TEXT);
    newField.setNewFieldRequest(true);
    ApiInventoryEntityField toDelete = new ApiInventoryEntityField();
    toDelete.setId(existingFieldId);
    toDelete.setDeleteFieldRequest(true);
    template.setFields(List.of(toDelete, newField));

    ApiInstrumentTemplate updated =
        instrumentApiMgr.updateApiInstrumentTemplate(template, testUser);

    assertEquals(1, updated.getFields().size());
    assertEquals("added", updated.getFields().get(0).getName());
  }

  @Test
  public void markInstrumentTemplateAsDeleted_andRestore() {
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(testUser);
    assertFalse(template.isDeleted());

    ApiInstrumentTemplate deleted =
        instrumentApiMgr.markInstrumentTemplateAsDeleted(template.getId(), testUser);
    assertTrue(deleted.isDeleted());
    verify(mockPublisher).publishEvent(Mockito.any(InventoryDeleteEvent.class));

    ApiInstrumentTemplate restored =
        instrumentApiMgr.restoreDeletedInstrumentTemplate(template.getId(), testUser);
    assertFalse(restored.isDeleted());
    verify(mockPublisher).publishEvent(Mockito.any(InventoryRestoreEvent.class));
  }

  @Test
  public void duplicateInstrumentTemplate_createsCopy() {
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(testUser);

    ApiInstrumentTemplate copy =
        instrumentApiMgr.duplicateInstrumentTemplate(template.getId(), testUser);

    assertNotNull(copy.getId());
    assertFalse(copy.getId().equals(template.getId()));
    assertTrue(copy.isTemplate());
    verify(mockPublisher, Mockito.times(2)).publishEvent(Mockito.any(InventoryCreationEvent.class));
  }

  @Test
  public void changeApiInstrumentTemplateOwner_transfersOwnership() {
    User newOwner = createAndSaveUserIfNotExists(getRandomAlphabeticString("ntOwner"));
    initialiseContentWithEmptyContent(newOwner);
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(testUser);

    template.setOwner(new ApiUser(newOwner));
    ApiInstrumentTemplate transferred =
        instrumentApiMgr.changeApiInstrumentTemplateOwner(template, testUser);

    assertEquals(newOwner.getUsername(), transferred.getOwner().getUsername());
    verify(mockPublisher).publishEvent(Mockito.any(InventoryTransferEvent.class));
  }

  @Test
  public void templateNameExistsForUser_detectsExistingName() {
    String uniqueName = "unique-template-" + getRandomAlphabeticString("n");
    assertFalse(instrumentApiMgr.templateNameExistsForUser(uniqueName, testUser));

    createBasicInstrumentTemplateForUser(testUser, uniqueName);

    assertTrue(instrumentApiMgr.templateNameExistsForUser(uniqueName, testUser));
  }

  @Test
  public void updateInstrumentToLatestTemplateVersion_resyncsAfterTemplateChange() {
    // Create a template, then an instrument from it
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(testUser);
    ApiInstrument instrumentReq = new ApiInstrument();
    instrumentReq.setName("to-be-resynced");
    instrumentReq.setTemplateId(template.getId());
    ApiInstrument instrument = instrumentApiMgr.createNewApiInstrument(instrumentReq, testUser);
    assertEquals(template.getVersion(), instrument.getTemplateVersion());

    // Bump the template by adding a new field
    ApiInventoryEntityField newField = new ApiInventoryEntityField();
    newField.setName("extra-template-field");
    newField.setType(ApiFieldType.TEXT);
    newField.setNewFieldRequest(true);
    template.setFields(List.of(newField));
    ApiInstrumentTemplate updatedTemplate =
        instrumentApiMgr.updateApiInstrumentTemplate(template, testUser);
    assertTrue(updatedTemplate.getVersion() > instrument.getTemplateVersion());

    // Sync the instrument with the latest template version
    ApiInstrument resynced =
        instrumentApiMgr.updateInstrumentToLatestTemplateVersion(instrument.getId(), testUser);
    assertEquals(updatedTemplate.getVersion(), resynced.getTemplateVersion());
    // instrument now carries the new field
    assertTrue(
        resynced.getFields().stream().anyMatch(f -> "extra-template-field".equals(f.getName())));
  }

  @Test
  public void updateInstrumentToLatestTemplateVersion_propagatesEditedLinkWhitelist() {
    // RSDEV-1200: an existing instrument synced to a newer template version must pick up the
    // template's edited link-field allowed-relation-types whitelist, mirroring the sample path.
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("instr-link-tmpl-" + getRandomAlphabeticString("n"));
    ApiInventoryEntityField linkField = new ApiInventoryEntityField();
    linkField.setName("related");
    linkField.setType(ApiFieldType.LINK);
    linkField.setAllowedRelationTypes(List.of("References", "IsDerivedFrom"));
    templatePost.getFields().add(linkField);
    ApiInstrumentTemplate template =
        instrumentApiMgr.createInstrumentTemplate(templatePost, testUser);
    Long templateLinkFieldId = findInstrumentLinkField(template.getFields()).getId();

    // an instrument created before the edit inherits the original whitelist
    ApiInstrument instrumentReq = new ApiInstrument();
    instrumentReq.setName("existing-instrument");
    instrumentReq.setTemplateId(template.getId());
    ApiInstrument instrument = instrumentApiMgr.createNewApiInstrument(instrumentReq, testUser);
    assertEquals(
        List.of("References", "IsDerivedFrom"),
        findInstrumentLinkField(
                instrumentApiMgr.getApiInstrumentById(instrument.getId(), testUser).getFields())
            .getAllowedRelationTypes());

    // edit the template link-field whitelist (bumps the template version)
    ApiInventoryEntityField editField = new ApiInventoryEntityField();
    editField.setId(templateLinkFieldId);
    editField.setType(ApiFieldType.LINK);
    editField.setAllowedRelationTypes(List.of("IsCitedBy", "Cites"));
    ApiInstrumentTemplate edit = new ApiInstrumentTemplate();
    edit.setId(template.getId());
    edit.setFields(List.of(editField));
    instrumentApiMgr.updateApiInstrumentTemplate(edit, testUser);

    // sync the existing instrument and assert it acquired the edited whitelist
    instrumentApiMgr.updateInstrumentToLatestTemplateVersion(instrument.getId(), testUser);
    ApiInstrument synced = instrumentApiMgr.getApiInstrumentById(instrument.getId(), testUser);
    assertEquals(
        List.of("IsCitedBy", "Cites"),
        findInstrumentLinkField(synced.getFields()).getAllowedRelationTypes(),
        "existing instrument should acquire the template's edited whitelist after sync");
  }

  private ApiInventoryEntityField findInstrumentLinkField(List<ApiInventoryEntityField> fields) {
    return fields.stream()
        .filter(f -> ApiFieldType.LINK.equals(f.getType()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("expected a link field in the instrument fields"));
  }

  @Test
  public void getInstrumentsLinkingOldTemplateVersion_returnsLaggingInstruments() {
    // Create a template + an instrument from it (linked at version 1)
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(testUser);
    ApiInstrument instrumentReq = new ApiInstrument();
    instrumentReq.setName("lagging");
    instrumentReq.setTemplateId(template.getId());
    ApiInstrument lagging = instrumentApiMgr.createNewApiInstrument(instrumentReq, testUser);

    // Initially no lagging instruments (instrument is on latest version)
    assertTrue(
        instrumentApiMgr
            .getInstrumentsLinkingOldTemplateVersion(template.getId(), testUser)
            .isEmpty());

    // Bump the template — now the existing instrument is lagging
    template.setDescription("new description");
    instrumentApiMgr.updateApiInstrumentTemplate(template, testUser);

    List<ApiInventoryRecordInfo> lagged =
        instrumentApiMgr.getInstrumentsLinkingOldTemplateVersion(template.getId(), testUser);
    assertEquals(1, lagged.size());
    assertEquals(lagging.getId(), lagged.get(0).getId());
  }

  @Test
  public void updateApiInstrumentTemplate_throwsIfTargetIsNotATemplate() {
    ApiInstrument instrument = createBasicInstrumentForUser(testUser, "not-a-template");
    ApiInstrumentTemplate pretendTemplate = new ApiInstrumentTemplate();
    pretendTemplate.setId(instrument.getId());
    pretendTemplate.setName("hacking-attempt");

    // assertUserCanEditInstrumentTemplate casts to InstrumentTemplate; on an Instrument
    // record this should fail with a runtime exception (ClassCastException wrapped or similar)
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.updateApiInstrumentTemplate(pretendTemplate, testUser));
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
  public void moveInstrumentToListContainer() {
    ApiContainer listContainer = createBasicContainerForUser(testUser);
    ApiContainer workbench = getWorkbenchForUser(testUser);
    int initialWorkbenchCount = workbench.getContentSummary().getTotalCount();

    ApiInstrument created = createBasicInstrumentForUser(testUser, "move-test");
    assertEquals(workbench.getId(), created.getParentContainer().getId());

    ApiInstrument updateRequest = new ApiInstrument();
    updateRequest.setId(created.getId());
    updateRequest.setParentContainer(listContainer);
    ApiInstrument updated = instrumentApiMgr.updateApiInstrument(updateRequest, testUser);

    assertEquals(listContainer.getId(), updated.getParentContainer().getId());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryMoveEvent.class));

    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(1, listContainer.getContentSummary().getTotalCount());
    workbench = getWorkbenchForUser(testUser);
    // instrument moved out of workbench into list container (which stays on workbench),
    // so workbench direct-child count is the same as before the instrument was created
    assertEquals(initialWorkbenchCount, workbench.getContentSummary().getTotalCount());
  }

  @Test
  public void instrumentUpdateRejectsDuplicateExtraFieldNames() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "update-dup-extras");

    ApiInstrument update = new ApiInstrument();
    update.setId(created.getId());
    ApiExtraField one = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    one.setName("dup");
    one.setContent("a");
    one.setNewFieldRequest(true);
    ApiExtraField two = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    two.setName("dup");
    two.setContent("b");
    two.setNewFieldRequest(true);
    update.setExtraFields(List.of(one, two));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> instrumentApiMgr.updateApiInstrument(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void instrumentUpdateRejectsCaseOnlyDuplicateExtraFieldNames() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "update-case-dup-extras");

    ApiInstrument update = new ApiInstrument();
    update.setId(created.getId());
    ApiExtraField one = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    one.setName("Foo");
    one.setContent("a");
    one.setNewFieldRequest(true);
    ApiExtraField two = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    two.setName("foo");
    two.setContent("b");
    two.setNewFieldRequest(true);
    update.setExtraFields(List.of(one, two));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> instrumentApiMgr.updateApiInstrument(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void instrumentUpdateTrimsBeforeCompare() {
    ApiInstrument created = createBasicInstrumentForUser(testUser, "update-trim-extras");

    ApiInstrument update = new ApiInstrument();
    update.setId(created.getId());
    ApiExtraField one = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    one.setName("Foo");
    one.setContent("a");
    one.setNewFieldRequest(true);
    ApiExtraField two = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    two.setName(" Foo ");
    two.setContent("b");
    two.setNewFieldRequest(true);
    update.setExtraFields(List.of(one, two));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> instrumentApiMgr.updateApiInstrument(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void instrumentUpdateRejectsAddingDuplicateOfExistingExtraField() {
    // seed an instrument with one extra "solo"
    ApiInstrument seed = new ApiInstrument();
    seed.setName("update-existing-dup-extras");
    ApiExtraField first = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    first.setName("solo");
    first.setContent("v1");
    seed.setExtraFields(List.of(first));
    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(seed, testUser);

    // PUT another new extra with the same name → caught by post-mutation entity check
    ApiInstrument update = new ApiInstrument();
    update.setId(created.getId());
    ApiExtraField duplicate = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    duplicate.setName("solo");
    duplicate.setContent("v2");
    duplicate.setNewFieldRequest(true);
    update.setExtraFields(List.of(duplicate));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> instrumentApiMgr.updateApiInstrument(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void instrumentUpdateAllowsDeleteAndAddSameExtraFieldName() {
    // seed with one extra "A"
    ApiInstrument seed = new ApiInstrument();
    seed.setName("update-delete-add-extras");
    ApiExtraField first = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    first.setName("A");
    first.setContent("v1");
    seed.setExtraFields(List.of(first));
    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(seed, testUser);
    Long existingId = created.getExtraFields().get(0).getId();

    // delete A and add a new A in the same PUT
    ApiInstrument update = new ApiInstrument();
    update.setId(created.getId());
    ApiExtraField toDelete = new ApiExtraField();
    toDelete.setId(existingId);
    toDelete.setDeleteFieldRequest(true);
    ApiExtraField replacement = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    replacement.setName("A");
    replacement.setContent("v2");
    replacement.setNewFieldRequest(true);
    update.setExtraFields(List.of(toDelete, replacement));

    ApiInstrument updated = instrumentApiMgr.updateApiInstrument(update, testUser);
    long activeAs =
        updated.getExtraFields().stream()
            .filter(ef -> "A".equals(ef.getName()))
            .filter(ef -> "v2".equals(ef.getContent()))
            .count();
    assertEquals(1L, activeAs);
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

  @Test
  public void updateApiInstrumentTemplateWithBase64Image() {
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(testUser);

    ApiInstrumentTemplate update = new ApiInstrumentTemplate();
    update.setId(template.getId());
    update.setNewBase64Image(BASE_64_PNG);

    // Before the fix this threw ClassCastException; the result must be non-null after the fix.
    ApiInstrumentTemplate updated = instrumentApiMgr.updateApiInstrumentTemplate(update, testUser);
    assertNotNull(updated);
    assertEquals(template.getId(), updated.getId());
  }

  @Test
  public void assertUserCanReadInstrument_withTemplateId_throwsNotFoundNotClassCast() {
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(testUser);
    assertThrows(
        jakarta.ws.rs.NotFoundException.class,
        () -> instrumentApiMgr.assertUserCanReadInstrument(template.getId(), testUser));
  }

  @Test
  public void assertUserCanReadInstrumentTemplate_withInstrumentId_throwsNotFoundNotClassCast() {
    ApiInstrument instrument = createBasicInstrumentForUser(testUser, "type-mismatch-test");
    assertThrows(
        jakarta.ws.rs.NotFoundException.class,
        () -> instrumentApiMgr.assertUserCanReadInstrumentTemplate(instrument.getId(), testUser));
  }

  @Test
  public void assertUserCanRead_eachTypeAccessibleIndependentlyWhenBothExist() {
    ApiInstrument instrument = createBasicInstrumentForUser(testUser, "collision-instrument");
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(testUser);

    Instrument dbInstrument =
        instrumentApiMgr.assertUserCanReadInstrument(instrument.getId(), testUser);
    assertNotNull(dbInstrument);
    assertEquals(instrument.getId(), dbInstrument.getId());

    InstrumentTemplate dbTemplate =
        instrumentApiMgr.assertUserCanReadInstrumentTemplate(template.getId(), testUser);
    assertNotNull(dbTemplate);
    assertEquals(template.getId(), dbTemplate.getId());
  }

  /**
   * Regression: {@code duplicateInstrument} returned {@code new ApiInstrument(copy)} without
   * calling {@code populateOutgoingApiInstrumentEntity}, stripping {@code permittedActions} and
   * owner/sharing fields from the response. Every sibling method ({@code createInstrument}, {@code
   * updateApiInstrument}, {@code markInstrumentAsDeleted}, etc.) and {@code
   * duplicateInstrumentTemplate} all call the populate helper.
   */
  @Test
  public void duplicateInstrumentResponseIncludesPermittedActionsAndOwner() {
    ApiInstrument original = createBasicInstrumentForUser(testUser, "dup-populate-test");

    ApiInstrument copy = instrumentApiMgr.duplicateInstrument(original.getId(), testUser);

    assertNotNull(copy.getId());
    // permittedActions is set by populateOutgoingApiInstrumentEntity; empty without the call
    assertNotNull(copy.getPermittedActions());
    assertFalse(copy.getPermittedActions().isEmpty());
    // owner is also populated by the helper
    assertNotNull(copy.getOwner());
    assertEquals(testUser.getUsername(), copy.getOwner().getUsername());
  }

  @Test
  public void createInstrumentTemplateWithRadioField_persistsSuccessfully() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("radio-field-template");

    ApiInventoryEntityField radioField = new ApiInventoryEntityField();
    radioField.setName("Status");
    radioField.setType(ApiFieldType.RADIO);
    ApiInventoryEntityField.ApiInventoryFieldDef def =
        new ApiInventoryEntityField.ApiInventoryFieldDef();
    def.setOptions(List.of("Active", "Inactive", "Maintenance"));
    radioField.setDefinition(def);
    templatePost.getFields().add(radioField);

    ApiInstrumentTemplate created =
        instrumentApiMgr.createInstrumentTemplate(templatePost, testUser);

    assertNotNull(created.getId());
    assertTrue(instrumentApiMgr.instrumentTemplateExists(created.getId()));
    assertEquals(1, created.getFields().size());
    assertEquals("Status", created.getFields().get(0).getName());
    assertEquals(ApiFieldType.RADIO, created.getFields().get(0).getType());
  }

  // --- link field persistence (create / update / clear paths) ---

  @Test
  public void linkFieldValue_persistedWhenInstrumentCreatedFromTemplate() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("link-tmpl-create-" + getRandomAlphabeticString("n"));
    ApiInventoryEntityField linkField = new ApiInventoryEntityField();
    linkField.setName("ref");
    linkField.setType(ApiFieldType.LINK);
    templatePost.getFields().add(linkField);
    ApiInstrumentTemplate template =
        instrumentApiMgr.createInstrumentTemplate(templatePost, testUser);

    ApiSampleWithFullSubSamples target = createBasicSampleForUser(testUser, "link-create-target");

    ApiInstrument request = new ApiInstrument();
    request.setName("linked-from-create");
    request.setTemplateId(template.getId());
    ApiInventoryEntityField fieldWithLink = new ApiInventoryEntityField();
    ApiInventoryLink apiLink = new ApiInventoryLink();
    apiLink.setTargetGlobalId(target.getGlobalId());
    apiLink.setRelationType("References");
    fieldWithLink.setLink(apiLink);
    request.setFields(List.of(fieldWithLink));

    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);

    ApiInstrument retrieved = instrumentApiMgr.getApiInstrumentById(created.getId(), testUser);
    ApiInventoryLink storedLink = findInstrumentLinkField(retrieved.getFields()).getLink();
    assertNotNull(storedLink, "link should be persisted on the field after creation");
    assertEquals(target.getGlobalId(), storedLink.getTargetGlobalId());
    assertEquals("References", storedLink.getRelationType());
  }

  @Test
  public void linkFieldValue_persistedWhenInstrumentUpdated() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("link-tmpl-update-" + getRandomAlphabeticString("n"));
    ApiInventoryEntityField linkField = new ApiInventoryEntityField();
    linkField.setName("ref");
    linkField.setType(ApiFieldType.LINK);
    templatePost.getFields().add(linkField);
    ApiInstrumentTemplate template =
        instrumentApiMgr.createInstrumentTemplate(templatePost, testUser);

    ApiInstrument request = new ApiInstrument();
    request.setName("to-be-linked");
    request.setTemplateId(template.getId());
    ApiInstrument instrument = instrumentApiMgr.createNewApiInstrument(request, testUser);
    assertNull(findInstrumentLinkField(instrument.getFields()).getLink(), "no link before update");

    ApiSampleWithFullSubSamples target = createBasicSampleForUser(testUser, "link-update-target");
    Long fieldId = findInstrumentLinkField(instrument.getFields()).getId();

    ApiInventoryEntityField fieldUpdate = new ApiInventoryEntityField();
    fieldUpdate.setId(fieldId);
    ApiInventoryLink apiLink = new ApiInventoryLink();
    apiLink.setTargetGlobalId(target.getGlobalId());
    apiLink.setRelationType("References");
    fieldUpdate.setLink(apiLink);
    ApiInstrument update = new ApiInstrument();
    update.setId(instrument.getId());
    update.setFields(List.of(fieldUpdate));
    instrumentApiMgr.updateApiInstrument(update, testUser);

    ApiInstrument retrieved = instrumentApiMgr.getApiInstrumentById(instrument.getId(), testUser);
    ApiInventoryLink storedLink = findInstrumentLinkField(retrieved.getFields()).getLink();
    assertNotNull(storedLink, "link should be persisted after update");
    assertEquals(target.getGlobalId(), storedLink.getTargetGlobalId());
    assertEquals("References", storedLink.getRelationType());
  }

  @Test
  public void linkFieldValue_clearedWhenInstrumentUpdated() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("link-tmpl-clear-" + getRandomAlphabeticString("n"));
    ApiInventoryEntityField linkField = new ApiInventoryEntityField();
    linkField.setName("ref");
    linkField.setType(ApiFieldType.LINK);
    templatePost.getFields().add(linkField);
    ApiInstrumentTemplate template =
        instrumentApiMgr.createInstrumentTemplate(templatePost, testUser);

    ApiSampleWithFullSubSamples target = createBasicSampleForUser(testUser, "link-clear-target");

    ApiInstrument request = new ApiInstrument();
    request.setName("to-be-cleared");
    request.setTemplateId(template.getId());
    ApiInventoryEntityField fieldWithLink = new ApiInventoryEntityField();
    ApiInventoryLink apiLink = new ApiInventoryLink();
    apiLink.setTargetGlobalId(target.getGlobalId());
    apiLink.setRelationType("References");
    fieldWithLink.setLink(apiLink);
    request.setFields(List.of(fieldWithLink));
    ApiInstrument instrument = instrumentApiMgr.createNewApiInstrument(request, testUser);
    assertNotNull(
        findInstrumentLinkField(instrument.getFields()).getLink(), "link set on creation");

    Long fieldId = findInstrumentLinkField(instrument.getFields()).getId();
    ApiInventoryEntityField clearField = new ApiInventoryEntityField();
    clearField.setId(fieldId);
    // no link payload: clears the existing link
    ApiInstrument clearUpdate = new ApiInstrument();
    clearUpdate.setId(instrument.getId());
    clearUpdate.setFields(List.of(clearField));
    instrumentApiMgr.updateApiInstrument(clearUpdate, testUser);

    ApiInstrument retrieved = instrumentApiMgr.getApiInstrumentById(instrument.getId(), testUser);
    assertNull(
        findInstrumentLinkField(retrieved.getFields()).getLink(),
        "link should be cleared after update with no link payload");
  }
}
