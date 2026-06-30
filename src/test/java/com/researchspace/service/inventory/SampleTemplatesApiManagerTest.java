package com.researchspace.service.inventory;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryEntityField.ApiInventoryFieldDef;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleTemplateSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSampleAlias;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSampleName;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

public class SampleTemplatesApiManagerTest extends SpringTransactionalTest {

  private User testUser;

  @Before
  public void setUp() {
    sampleTemplateDao.resetDefaultTemplateOwner();

    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());
  }

  @Test
  public void checkTemplateFiltering_rsinv131() {
    PaginationCriteria<SampleTemplate> defaultPgCrit =
        PaginationCriteria.createDefaultForClass(SampleTemplate.class);
    defaultPgCrit.setSortOrder(SortOrder.ASC);
    User firstUser = createInitAndLoginAnyUser();
    ApiSampleTemplateSearchResult initialTemplates =
        sampleApiMgr.getTemplatesForUser(defaultPgCrit, null, null, firstUser);
    final long initTemplateCount = initialTemplates.getTotalHits();

    // create additional template
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("AaaNewTemplate");
    sampleTemplatePost.setApiTagInfo("tag1,tag2");
    sampleTemplatePost.setDefaultUnitId(RSUnitDef.GRAM.getId());
    sampleTemplatePost.setSampleSource(SampleSource.LAB_CREATED);
    sampleApiMgr.createSampleTemplate(sampleTemplatePost, firstUser);

    ApiSampleTemplateSearchResult firstUserTemplates =
        sampleApiMgr.getTemplatesForUser(defaultPgCrit, null, null, firstUser);
    long updatedTemplateCount = firstUserTemplates.getTotalHits();
    assertEquals(initTemplateCount + 1, updatedTemplateCount);
    // user has permission to their own template...
    assertEquals(sampleTemplatePost.getName(), firstUserTemplates.getTemplates().get(0).getName());
    assertEquals(3, firstUserTemplates.getTemplates().get(0).getPermittedActions().size());
    // ... but only read premission to default templates
    assertEquals(1, firstUserTemplates.getTemplates().get(1).getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordPermittedAction.READ,
        firstUserTemplates.getTemplates().get(1).getPermittedActions().get(0));

    // other user can't see new template, just the same default templates as first user
    User otherUser = createInitAndLoginAnyUser();
    long otherUserTemplateCount =
        sampleApiMgr.getTemplatesForUser(defaultPgCrit, null, null, otherUser).getTotalHits();
    assertEquals(initTemplateCount, otherUserTemplateCount);
  }

  @Test
  public void whitelistedTemplateVisibilityInGroups() {
    // create users and two groups
    User pi1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User pi2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi2"), Constants.PI_ROLE);
    User user1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user1"));
    User user2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user2"));
    User user3 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user3"));
    User user4 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user4"));
    initialiseContentWithEmptyContent(pi1, pi2, user1, user2, user3, user4);

    Group groupA = createGroup("groupA", pi1);
    addUsersToGroup(pi1, groupA, user1, user2);
    Group groupB = createGroup("groupB", pi2);
    addUsersToGroup(pi2, groupB, user3, user4);

    PaginationCriteria<SampleTemplate> defaultPgCrit =
        PaginationCriteria.createDefaultForClass(SampleTemplate.class);
    defaultPgCrit.setSortOrder(SortOrder.ASC);
    ApiSampleTemplateSearchResult initialTemplates =
        sampleApiMgr.getTemplatesForUser(defaultPgCrit, null, null, user1);
    final long initTemplateCount = initialTemplates.getTotalHits();

    // as user1 create additional template
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("user1Template");
    sampleTemplatePost.setSharingMode(ApiInventoryRecordInfo.ApiInventorySharingMode.WHITELIST);
    sampleTemplatePost.setSharedWith(
        List.of(
            ApiInventoryRecordInfo.ApiGroupInfoWithSharedFlag.forSharingWithGroup(groupB, user1)));
    ApiSampleTemplate user1Template = sampleApiMgr.createSampleTemplate(sampleTemplatePost, user1);
    assertNotNull(user1Template.getGlobalId());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.WHITELIST, user1Template.getSharingMode());
    assertEquals(2, user1Template.getSharedWith().size());

    // verify permissions,
    SampleTemplate createdTemplate =
        sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(user1Template.getId(), user1);
    assertTrue(
        invPermissionUtils.canUserEditInventoryRecord(
            createdTemplate, user1)); // edit permission as owner
    assertFalse(
        invPermissionUtils.canUserReadOrLimitedReadInventoryRecord(
            createdTemplate, user2)); // no permission
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            createdTemplate, pi1)); // read permission as user1's PI...
    assertFalse(
        invPermissionUtils.canUserEditInventoryRecord(createdTemplate, pi1)); // ...but cannot edit
    assertTrue(
        invPermissionUtils.canUserEditInventoryRecord(
            createdTemplate, user3)); // edit permission as member of groupB

    // user1 should find own template fine
    ApiSampleTemplateSearchResult foundTemplates =
        sampleApiMgr.getTemplatesForUser(defaultPgCrit, null, null, user1);
    assertEquals(initTemplateCount + 1, foundTemplates.getTotalHits());
    // pi should also see user1's template, as they are user's pi
    foundTemplates = sampleApiMgr.getTemplatesForUser(defaultPgCrit, null, null, pi1);
    assertEquals(initTemplateCount + 1, foundTemplates.getTotalHits());
    // user2 shouldn't see the new template
    foundTemplates = sampleApiMgr.getTemplatesForUser(defaultPgCrit, null, null, user2);
    assertEquals(initTemplateCount, foundTemplates.getTotalHits());
    // user3 is member of groupB, so should see the new template
    foundTemplates = sampleApiMgr.getTemplatesForUser(defaultPgCrit, null, null, user3);
    assertEquals(initTemplateCount + 1, foundTemplates.getTotalHits());
  }

  @Test
  public void checkTemplatePermissionsAndLimitedView() {

    // create a pi and other user
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User mainUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(piUser, mainUser, otherUser);

    // groupA with pi and test user
    Group groupA = createGroup("groupA", piUser);
    addUsersToGroup(piUser, groupA, mainUser);
    // groupB with pi and other user
    Group groupB = createGroup("groupB", piUser);
    addUsersToGroup(piUser, groupB, otherUser);

    // create a template, check default sharing
    ApiSampleTemplatePost sampleTemplatePost = getTemplatePostForTestTemplateWithTextField();
    ApiSampleTemplate createdTemplate =
        sampleApiMgr.createSampleTemplate(sampleTemplatePost, mainUser);
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.OWNER_GROUPS,
        createdTemplate.getSharingMode());
    assertNotNull(createdTemplate.getSharedWith());
    assertEquals(1, createdTemplate.getSharedWith().size());
    assertEquals("groupA", createdTemplate.getSharedWith().get(0).getGroupInfo().getName());
    assertFalse(createdTemplate.getSharedWith().get(0).isShared());
    assertTrue(createdTemplate.getSharedWith().get(0).isItemOwnerGroup());

    // confirm other user can only see public details
    ApiSample templateRetrievedByOtherUser =
        sampleApiMgr.getApiSampleTemplateById(createdTemplate.getId(), otherUser);
    assertTrue(templateRetrievedByOtherUser.isClearedForPublicView());

    // create a sample from the template, share the sample with group B
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample from template v1");
    apiSample.setTemplateId(createdTemplate.getId());
    setItemSharedModeToWhitelistWithGroups(apiSample, List.of(groupB), mainUser);
    ApiSampleWithFullSubSamples createdSample =
        sampleApiMgr.createNewApiSample(apiSample, mainUser);
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.WHITELIST, createdSample.getSharingMode());

    // confirm that other user can read the sample, and limited-read the template
    ApiSample sampleRetrievedByOtherUser =
        sampleApiMgr.getApiSampleById(createdSample.getId(), otherUser);
    assertEquals(2, sampleRetrievedByOtherUser.getPermittedActions().size());
    templateRetrievedByOtherUser =
        sampleApiMgr.getApiSampleTemplateById(createdTemplate.getId(), otherUser);
    assertFalse(templateRetrievedByOtherUser.isClearedForPublicView());
    assertTrue(templateRetrievedByOtherUser.isClearedForLimitedView());
  }

  @Test
  public void checkSampleTemplateCannotDuplicateDefaultFieldNames() {

    ApiSampleTemplatePost sampleTemplatePost = getTemplatePostForTestTemplateWithTextField();
    sampleTemplatePost
        .getFields()
        .add(createBasicApiSampleField("string", ApiFieldType.STRING, "string value"));

    // should create template fine
    ApiSampleTemplate createdTemplate =
        sampleApiMgr.createSampleTemplate(sampleTemplatePost, testUser);
    assertEquals(2, createdTemplate.getFields().size());

    sampleTemplatePost
        .getFields()
        .add(createBasicApiSampleField("Description", ApiFieldType.TEXT, "text value"));
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.createSampleTemplate(sampleTemplatePost, testUser));
    assertEquals(
        "'Description' is not a valid name for a field, "
            + "as there is a default property with this name.",
        iae.getMessage());
  }

  // RSDEV-1066: Inventory fields must have unique names
  // -----------------------------------------------------------------

  @Test
  public void templateCreateRejectsDuplicateSampleFieldNames() {
    ApiSampleTemplatePost post = getTemplatePostForTestTemplateWithTextField();
    post.getFields().add(createBasicApiSampleField("text", ApiFieldType.STRING, "another text"));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> sampleApiMgr.createSampleTemplate(post, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void templatePutRejectsExtraFieldWithSameNameAsSampleField() {
    // ApiSampleTemplatePost cannot carry extraFields (POST surface), so the cross-collection
    // collision is exercised via PUT, which uses ApiSampleTemplate (extends ApiSample, which
    // includes extraFields).
    ApiSampleTemplatePost post = getTemplatePostForTestTemplateWithTextField();
    ApiSampleTemplate created = sampleApiMgr.createSampleTemplate(post, testUser);

    ApiSampleTemplate update = new ApiSampleTemplate();
    update.setId(created.getId());
    ApiExtraField extra = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    extra.setName("text"); // same name as the existing SampleField
    extra.setContent("collides with sample field");
    extra.setNewFieldRequest(true);
    update.setExtraFields(List.of(extra));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> sampleApiMgr.updateApiSampleTemplate(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void templateCreateRejectsFieldNamedAfterUILabel() {
    // RSDEV-1066: SampleTemplate's displayed-label set includes "Subsample Alias", so
    // core-model's verifyFieldNameAllowed rejects the field at Sample.addSampleField time
    // with the legacy reserved-name IAE.
    ApiSampleTemplatePost post = getTemplatePostForTestTemplateWithTextField();
    post.getFields().add(createBasicApiSampleField("Subsample Alias", ApiFieldType.STRING, "v"));

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.createSampleTemplate(post, testUser));
    assertEquals(
        "'Subsample Alias' is not a valid name for a field, "
            + "as there is a default property with this name.",
        iae.getMessage());
  }

  @Test
  public void templateCreateTrimsBeforeCompare() {
    ApiSampleTemplatePost post = getTemplatePostForTestTemplateWithTextField();
    post.getFields().add(createBasicApiSampleField(" text", ApiFieldType.STRING, "leading space"));

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class, () -> sampleApiMgr.createSampleTemplate(post, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void templatePutRejectsAddingDuplicateOfExistingFieldName() {
    ApiSampleTemplatePost post = getTemplatePostForTestTemplateWithTextField();
    ApiSampleTemplate created = sampleApiMgr.createSampleTemplate(post, testUser);
    assertEquals(1, created.getFields().size());

    ApiSampleTemplate update = new ApiSampleTemplate();
    update.setId(created.getId());
    ApiInventoryEntityField newField =
        createBasicApiSampleField("text", ApiFieldType.STRING, "would collide");
    newField.setNewFieldRequest(true);
    update.getFields().add(newField);

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> sampleApiMgr.updateApiSampleTemplate(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void templatePutAllowsRenamingBToAWhileDeletingA() {
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("two-field template");
    post.getFields().add(createBasicApiSampleField("A", ApiFieldType.STRING, "av"));
    post.getFields().add(createBasicApiSampleField("B", ApiFieldType.STRING, "bv"));
    ApiSampleTemplate created = sampleApiMgr.createSampleTemplate(post, testUser);
    assertEquals(2, created.getFields().size());

    Long aFieldId = created.getFields().get(0).getId();
    Long bFieldId = created.getFields().get(1).getId();

    ApiSampleTemplate update = new ApiSampleTemplate();
    update.setId(created.getId());
    ApiInventoryEntityField deleteA = new ApiInventoryEntityField();
    deleteA.setId(aFieldId);
    deleteA.setDeleteFieldRequest(true);
    update.getFields().add(deleteA);
    ApiInventoryEntityField renameB = new ApiInventoryEntityField();
    renameB.setId(bFieldId);
    renameB.setName("A");
    update.getFields().add(renameB);

    ApiSampleTemplate result = sampleApiMgr.updateApiSampleTemplate(update, testUser);
    assertEquals(1, result.getFields().size());
    assertEquals("A", result.getFields().get(0).getName());
  }

  @Test
  public void templatePutRejectsRenamingTwoFieldsToSameName() {
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("two-field template");
    post.getFields().add(createBasicApiSampleField("A", ApiFieldType.STRING, "av"));
    post.getFields().add(createBasicApiSampleField("B", ApiFieldType.STRING, "bv"));
    ApiSampleTemplate created = sampleApiMgr.createSampleTemplate(post, testUser);

    ApiSampleTemplate update = new ApiSampleTemplate();
    update.setId(created.getId());
    ApiInventoryEntityField renameB = new ApiInventoryEntityField();
    renameB.setId(created.getFields().get(1).getId());
    renameB.setName("A");
    update.getFields().add(renameB);

    ApiRuntimeException are =
        assertThrows(
            ApiRuntimeException.class,
            () -> sampleApiMgr.updateApiSampleTemplate(update, testUser));
    assertEquals("errors.inventory.field.duplicate.name", are.getMessage());
  }

  @Test
  public void createUpdateSampleTemplate() throws InterruptedException {

    ApiSampleTemplatePost sampleTemplatePost = getTemplatePostForTestTemplateWithTextField();
    sampleTemplatePost
        .getFields()
        .add(createBasicApiSampleField("string", ApiFieldType.STRING, "string value"));
    ApiInventoryEntityField radioField =
        createBasicApiSampleOptionsField("radio", ApiFieldType.RADIO, List.of("r2{2.2}"));
    ApiInventoryFieldDef radioDef =
        new ApiInventoryFieldDef(List.of("r1[1,1]", "r2{2.2}", "r3(=3&3)"), false);
    radioField.setDefinition(radioDef);
    sampleTemplatePost.getFields().add(radioField);

    // should create template fine
    ApiSampleTemplate createdTemplate =
        sampleApiMgr.createSampleTemplate(sampleTemplatePost, testUser);
    assertEquals(3, createdTemplate.getFields().size());
    assertEquals(3, createdTemplate.getPermittedActions().size());

    // retrieve the template
    ApiSampleTemplate retrievedTemplate =
        sampleApiMgr.getApiSampleTemplateById(createdTemplate.getId(), testUser);
    assertEquals(
        SubSampleName.SUBSAMPLE.getDisplayName(), retrievedTemplate.getSubSampleAlias().getAlias());
    assertEquals(RSUnitDef.MILLI_LITRE.getId(), retrievedTemplate.getDefaultUnitId());
    assertEquals(3, retrievedTemplate.getPermittedActions().size());
    assertEquals(1, retrievedTemplate.getVersion());
    assertEquals(3, retrievedTemplate.getFields().size());
    ApiInventoryEntityField firstField = retrievedTemplate.getFields().get(0);
    assertEquals("text", firstField.getName());
    assertEquals("text value", firstField.getContent());
    ApiInventoryEntityField secondField = retrievedTemplate.getFields().get(1);
    assertEquals("string", secondField.getName());
    assertEquals("string value", secondField.getContent());
    assertFalse(secondField.getMandatory());
    ApiInventoryEntityField thirdField = retrievedTemplate.getFields().get(2);
    assertEquals("radio", thirdField.getName());
    assertEquals(null, thirdField.getContent());
    assertEquals(
        List.of("r1[1,1]", "r2{2.2}", "r3(=3&3)"), thirdField.getDefinition().getOptions());
    assertEquals(List.of("r2{2.2}"), thirdField.getSelectedOptions());

    // create sample from template
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample from template v1");
    apiSample.setTemplateId(retrievedTemplate.getId());
    ApiSampleWithFullSubSamples createdSample =
        sampleApiMgr.createNewApiSample(apiSample, testUser);
    assertNotNull(createdSample);
    assertEquals("sample from template v1", createdSample.getName());
    assertEquals("subsample", createdSample.getSubSampleAlias().getAlias());
    assertEquals("subsamples", createdSample.getSubSampleAlias().getPlural());
    assertEquals("1 ml", createdSample.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(1, createdSample.getVersion());
    assertEquals(retrievedTemplate.getId(), createdSample.getTemplateId());
    assertEquals(1, createdSample.getTemplateVersion());
    assertEquals(3, createdSample.getFields().size());
    assertEquals("text", createdSample.getFields().get(0).getName());
    assertEquals("string", createdSample.getFields().get(1).getName());
    assertEquals("radio", createdSample.getFields().get(2).getName());

    // prepare template update
    ApiSampleTemplate templateUpdates = new ApiSampleTemplate();
    templateUpdates.setId(retrievedTemplate.getId());
    templateUpdates.setName("updated template");
    templateUpdates.setSubSampleAlias(
        new ApiSubSampleAlias(
            SubSampleName.PIECE.getDisplayName(), SubSampleName.PIECE.getDisplayNamePlural()));
    templateUpdates.setDefaultUnitId(RSUnitDef.GRAM.getId());

    // prepare fields update (delete first, modify second and third, add fourth)
    ApiInventoryEntityField toDeleteField = new ApiInventoryEntityField();
    toDeleteField.setId(firstField.getId());
    toDeleteField.setDeleteFieldRequest(true);
    ApiInventoryEntityField toModifyField = new ApiInventoryEntityField();
    toModifyField.setId(secondField.getId());
    toModifyField.setName("updated string");
    toModifyField.setMandatory(true);
    ApiInventoryEntityField toModifyField2 = new ApiInventoryEntityField();
    toModifyField2.setId(thirdField.getId());
    toModifyField2.setDefinition(
        new ApiInventoryFieldDef(List.of("r2{2.2}", "r3(=3&3)", "r4(!4)"), false));
    ApiInventoryEntityField newField =
        createBasicApiSampleField("number", ApiFieldType.NUMBER, "3.14");
    newField.setColumnIndex(1); // set it as a first field of the template
    newField.setNewFieldRequest(true);
    templateUpdates.getFields().add(toDeleteField);
    templateUpdates.getFields().add(toModifyField);
    templateUpdates.getFields().add(toModifyField2);
    templateUpdates.getFields().add(newField);

    // run the update
    Thread.sleep(10); // ensure it's later
    ApiSampleTemplate updatedTemplate =
        sampleApiMgr.updateApiSampleTemplate(templateUpdates, testUser);
    assertNotNull(updatedTemplate);
    assertEquals(retrievedTemplate.getGlobalId(), updatedTemplate.getGlobalId());
    assertEquals("updated template", updatedTemplate.getName());
    assertEquals(
        SubSampleName.PIECE.getDisplayName(), updatedTemplate.getSubSampleAlias().getAlias());
    assertEquals(RSUnitDef.GRAM.getId(), updatedTemplate.getDefaultUnitId());
    assertTrue(updatedTemplate.getLastModifiedMillis() > retrievedTemplate.getLastModifiedMillis());
    assertEquals(2, updatedTemplate.getVersion());
    assertEquals(3, updatedTemplate.getFields().size());
    firstField = updatedTemplate.getFields().get(0);
    assertEquals("number", firstField.getName());
    assertEquals(1, firstField.getColumnIndex());
    secondField = updatedTemplate.getFields().get(1);
    assertEquals("updated string", secondField.getName());
    assertTrue(secondField.getMandatory());
    assertEquals(2, secondField.getColumnIndex());
    thirdField = updatedTemplate.getFields().get(2);
    assertEquals("radio", thirdField.getName());
    assertEquals(List.of("r2{2.2}", "r3(=3&3)", "r4(!4)"), thirdField.getDefinition().getOptions());
    assertEquals(List.of("r2{2.2}"), thirdField.getSelectedOptions());

    // create sample from updated template
    ApiSampleWithFullSubSamples apiSample2 =
        new ApiSampleWithFullSubSamples("sample from template v2");
    apiSample2.setTemplateId(retrievedTemplate.getId());
    ApiSampleWithFullSubSamples createdSample2 =
        sampleApiMgr.createNewApiSample(apiSample2, testUser);
    assertNotNull(createdSample2);
    assertEquals("piece", createdSample2.getSubSampleAlias().getAlias());
    assertEquals("1 g", createdSample2.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(1, createdSample2.getVersion());
    assertEquals(retrievedTemplate.getId(), createdSample2.getTemplateId());
    assertEquals(2, createdSample2.getTemplateVersion());
    assertEquals(3, createdSample2.getFields().size());
    assertEquals("number", createdSample2.getFields().get(0).getName());
    assertEquals("updated string", createdSample2.getFields().get(1).getName());
    assertTrue(createdSample2.getFields().get(1).getMandatory());
    assertEquals("radio", createdSample2.getFields().get(2).getName());
  }

  @Test
  public void saveUpdateSubSampleAlias() {

    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("test template");

    // try creating template with one of aliases blank
    sampleTemplatePost.setSubSampleAlias(new ApiSubSampleAlias("piece(tm)", " "));
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.createSampleTemplate(sampleTemplatePost, testUser));
    assertEquals("SubSample alias (plural) cannot be blank", iae.getMessage());
    sampleTemplatePost.setSubSampleAlias(new ApiSubSampleAlias(null, "pieces(tm)"));
    NullPointerException npe =
        assertThrows(
            NullPointerException.class,
            () -> sampleApiMgr.createSampleTemplate(sampleTemplatePost, testUser));
    assertEquals("SubSample alias cannot be blank", npe.getMessage());

    // create with both aliases specified
    sampleTemplatePost.setSubSampleAlias(new ApiSubSampleAlias("piece(tm)", " pieces(tm) "));
    ApiSampleTemplate template = sampleApiMgr.createSampleTemplate(sampleTemplatePost, testUser);
    assertEquals("piece(tm)", template.getSubSampleAlias().getAlias());
    assertEquals("pieces(tm)", template.getSubSampleAlias().getPlural()); // trimmed

    // try updating with alias not set
    ApiSampleTemplate update = new ApiSampleTemplate();
    update.setId(template.getId());
    update.setSubSampleAlias(new ApiSubSampleAlias(" ", "piece2(tm)"));
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.updateApiSampleTemplate(update, testUser));

    // update with both aliases specified
    update.setSubSampleAlias(new ApiSubSampleAlias("piece2(tm)\t", "pieces2(tm)"));
    ApiSampleTemplate updatedTemplate = sampleApiMgr.updateApiSampleTemplate(update, testUser);
    assertEquals("piece2(tm)", updatedTemplate.getSubSampleAlias().getAlias()); // trimmed
    assertEquals("pieces2(tm)", updatedTemplate.getSubSampleAlias().getPlural());
  }

  @Test
  public void sampleTemplateEditedByTwoUsers() {

    // create a pi, with a group
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(piUser);
    Group group = createGroup("group", piUser);
    addUsersToGroup(piUser, group, testUser);

    // create a template
    ApiSampleTemplatePost sampleTemplatePost = getTemplatePostForTestTemplateWithTextField();
    ApiSampleTemplate createdTemplate =
        sampleApiMgr.createSampleTemplate(sampleTemplatePost, testUser);
    ApiSampleTemplate testTemplate =
        sampleApiMgr.getApiSampleTemplateById(createdTemplate.getId(), testUser);
    assertEquals(1, testTemplate.getFields().size());

    // lock template by pi
    ApiInventoryEditLock apiLock =
        invLockTracker.attemptToLockForEdit(testTemplate.getGlobalId(), piUser);
    assertEquals(ApiInventoryEditLockStatus.LOCKED_OK, apiLock.getStatus());

    // try edit by testUser
    testTemplate.setName("updated name");
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.updateApiSampleTemplate(testTemplate, testUser));
    assertTrue(iae.getMessage().startsWith("Item is currently edited by another user ("));

    // try delete by testUser
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> sampleApiMgr.markSampleAsDeleted(testTemplate.getId(), false, testUser));
    assertTrue(iae.getMessage().startsWith("Item is currently edited by another user ("));

    // pi can edit fine
    ApiSample updatedTemplate = sampleApiMgr.updateApiSampleTemplate(testTemplate, piUser);
    assertEquals("updated name", updatedTemplate.getName());

    // pi unlocks
    invLockTracker.attemptToUnlock(testTemplate.getGlobalId(), piUser);

    // testUser can now edit fine
    testTemplate.setName("updated name 2");
    updatedTemplate = sampleApiMgr.updateApiSampleTemplate(testTemplate, testUser);
    assertEquals("updated name 2", updatedTemplate.getName());
  }

  @Test
  public void saveIconIdPersistsOnSampleTemplate() {
    ApiSampleTemplatePost sampleTemplatePost = getTemplatePostForTestTemplateWithTextField();
    ApiSampleTemplate createdTemplate =
        sampleApiMgr.createSampleTemplate(sampleTemplatePost, testUser);
    SampleTemplate template =
        sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(createdTemplate.getId(), testUser);
    assertEquals(Long.valueOf(-1L), template.getIconId()); // default icon id

    // RSDEV-1065 regression guard: saveIconId's DML must target SampleEntity; an update
    // scoped to the Sample subclass would silently no-op for template rows
    sampleApiMgr.saveIconId(template, 12345L);

    // bulk DML bypasses the session cache, so clear it before re-reading persisted state
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();
    SampleTemplate reloadedTemplate =
        sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(createdTemplate.getId(), testUser);
    assertEquals(Long.valueOf(12345L), reloadedTemplate.getIconId());
  }

  @NotNull
  private ApiSampleTemplatePost getTemplatePostForTestTemplateWithTextField() {
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("test template");
    sampleTemplatePost
        .getFields()
        .add(createBasicApiSampleField("text", ApiFieldType.TEXT, "text value"));
    return sampleTemplatePost;
  }

  @Test
  public void updateSampleToLatestVersion_addDeleteFields() throws InterruptedException {

    // create test template
    ApiSampleTemplate createdTemplate = createSampleTemplateWithRadioAndNumericFields(testUser);

    // create sample from template
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample from template v1");
    apiSample.setTemplateId(createdTemplate.getId());
    ApiSampleWithFullSubSamples createdSample =
        sampleApiMgr.createNewApiSample(apiSample, testUser);
    assertNotNull(createdSample);
    assertEquals(createdTemplate.getId(), createdSample.getTemplateId());
    assertEquals(1, createdSample.getTemplateVersion());
    assertEquals(2, createdSample.getFields().size());
    assertEquals("my radio", createdSample.getFields().get(0).getName());
    assertEquals("my number", createdSample.getFields().get(1).getName());
    assertEquals("3.14", createdSample.getFields().get(1).getContent());

    // update template
    ApiSampleTemplate templateUpdates = new ApiSampleTemplate();
    templateUpdates.setId(createdTemplate.getId());
    templateUpdates.setName("test template updated");
    // add a new text field
    ApiInventoryEntityField newTextField =
        createBasicApiSampleField("my text", ApiFieldType.TEXT, "default text");
    newTextField.setNewFieldRequest(true);
    templateUpdates.getFields().add(newTextField);
    // delete number field but only from future samples
    ApiInventoryEntityField numberFieldUpdates = new ApiInventoryEntityField();
    numberFieldUpdates.setId(createdTemplate.getFields().get(1).getId());
    numberFieldUpdates.setDeleteFieldRequest(true);
    templateUpdates.getFields().add(numberFieldUpdates);

    // update the template
    ApiSampleTemplate updatedTemplate =
        sampleApiMgr.updateApiSampleTemplate(templateUpdates, testUser);
    assertNotNull(updatedTemplate);
    assertEquals(2, updatedTemplate.getVersion());
    assertEquals(2, updatedTemplate.getFields().size());

    // create sample from updated template
    apiSample = new ApiSampleWithFullSubSamples("sample from template v1");
    apiSample.setTemplateId(createdTemplate.getId());
    ApiSampleWithFullSubSamples createdSampleV2 =
        sampleApiMgr.createNewApiSample(apiSample, testUser);
    assertEquals(createdTemplate.getId(), createdSampleV2.getTemplateId());
    assertEquals(2, createdSampleV2.getTemplateVersion());
    // the fields active in latest template definition are created
    assertEquals(2, createdSampleV2.getFields().size());
    assertEquals("my radio", createdSampleV2.getFields().get(0).getName());
    assertEquals("my text", createdSampleV2.getFields().get(1).getName());

    // retrieve sample
    ApiSample retrievedSample = sampleApiMgr.getApiSampleById(createdSample.getId(), testUser);
    assertEquals(createdTemplate.getId(), retrievedSample.getTemplateId());
    assertEquals(1, retrievedSample.getTemplateVersion());

    // try update to latest template
    sampleApiMgr.updateSampleToLatestTemplateVersion(createdSample.getId(), testUser);

    // check the sample
    ApiSample updatedSample =
        sampleApiMgr.updateSampleToLatestTemplateVersion(createdSample.getId(), testUser);
    assertEquals(createdTemplate.getId(), updatedSample.getTemplateId());
    assertEquals(2, updatedSample.getTemplateVersion());
    // field from latest definition is added, one deleted from definition still stays (was deleted
    // only for future samples)
    assertEquals(3, updatedSample.getFields().size());
    assertEquals("my radio", updatedSample.getFields().get(0).getName());
    assertEquals("my number", updatedSample.getFields().get(1).getName());
    assertEquals("my text", updatedSample.getFields().get(2).getName());
    assertNull(
        updatedSample
            .getFields()
            .get(2)
            .getContent()); // default value shouldn't be set for added field
  }

  @Test
  public void updateSampleToLatestVersion_deleteFields() throws InterruptedException {

    // create test template
    ApiSampleTemplate createdTemplate = createSampleTemplateWithRadioAndNumericFields(testUser);

    // create sample from template
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample from template v1");
    apiSample.setTemplateId(createdTemplate.getId());
    ApiSampleWithFullSubSamples createdSample =
        sampleApiMgr.createNewApiSample(apiSample, testUser);
    assertNotNull(createdSample);
    assertEquals(createdTemplate.getId(), createdSample.getTemplateId());
    assertEquals(1, createdSample.getTemplateVersion());
    assertEquals(2, createdSample.getFields().size());
    assertEquals("my radio", createdSample.getFields().get(0).getName());
    assertEquals("my number", createdSample.getFields().get(1).getName());
    assertEquals("3.14", createdSample.getFields().get(1).getContent());

    // update template
    ApiSampleTemplate templateUpdates = new ApiSampleTemplate();
    templateUpdates.setId(createdTemplate.getId());
    templateUpdates.setName("test template updated");
    // delete radio field but only for future samples
    ApiInventoryEntityField radioFieldDeletion = new ApiInventoryEntityField();
    radioFieldDeletion.setId(createdTemplate.getFields().get(0).getId());
    radioFieldDeletion.setDeleteFieldRequest(true);
    templateUpdates.getFields().add(radioFieldDeletion);

    // delete number field & delete from pre-existing samples
    ApiInventoryEntityField numberFieldDeletion = new ApiInventoryEntityField();
    numberFieldDeletion.setId(createdTemplate.getFields().get(1).getId());
    numberFieldDeletion.setDeleteFieldRequest(true);
    numberFieldDeletion.setDeleteFieldOnSampleUpdate(true);
    templateUpdates.getFields().add(numberFieldDeletion);

    // update the template
    ApiSampleTemplate updatedTemplate =
        sampleApiMgr.updateApiSampleTemplate(templateUpdates, testUser);
    assertNotNull(updatedTemplate);
    assertEquals(2, updatedTemplate.getVersion());
    assertEquals(0, updatedTemplate.getFields().size());

    // create new sample from template
    apiSample = new ApiSampleWithFullSubSamples("sample from template v2");
    apiSample.setTemplateId(createdTemplate.getId());
    ApiSampleWithFullSubSamples createdSampleV2 =
        sampleApiMgr.createNewApiSample(apiSample, testUser);
    assertEquals(createdTemplate.getId(), createdSampleV2.getTemplateId());
    assertEquals(2, createdSampleV2.getTemplateVersion());
    assertEquals(0, createdSampleV2.getFields().size());

    // retrieve pre-sample
    ApiSample retrievedSample = sampleApiMgr.getApiSampleById(createdSample.getId(), testUser);
    assertEquals(createdTemplate.getId(), retrievedSample.getTemplateId());
    assertEquals(1, retrievedSample.getTemplateVersion());
    assertEquals(2, retrievedSample.getFields().size());

    // update pre-existing sample to latest template definition
    sampleApiMgr.updateSampleToLatestTemplateVersion(createdSample.getId(), testUser);

    // check the sample - only number field should be deleted
    ApiSample updatedSample =
        sampleApiMgr.updateSampleToLatestTemplateVersion(createdSample.getId(), testUser);
    assertEquals(createdTemplate.getId(), updatedSample.getTemplateId());
    assertEquals(2, updatedSample.getTemplateVersion());
    assertEquals(1, updatedSample.getFields().size());
    assertEquals("my radio", updatedSample.getFields().get(0).getName());
  }

  @Test
  public void updateSampleToLatestVersion_modifyRadioOptions() throws InterruptedException {

    // prepare new template with radio & numeric field
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("test template");
    // add radio field
    ApiInventoryEntityField radioField =
        createBasicApiSampleOptionsField("my radio", ApiFieldType.RADIO, List.of("r1"));
    ApiInventoryFieldDef radioDef = new ApiInventoryFieldDef(List.of("r1", "r2", "r3"), false);
    radioField.setDefinition(radioDef);
    sampleTemplatePost.getFields().add(radioField);
    ApiInventoryEntityField numberField =
        createBasicApiSampleField("my number", ApiFieldType.NUMBER, "3.14");
    sampleTemplatePost.getFields().add(numberField);

    // create template fine
    ApiSampleTemplate createdTemplate =
        sampleApiMgr.createSampleTemplate(sampleTemplatePost, testUser);
    assertEquals(2, createdTemplate.getFields().size());

    // create sample from template
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample from template v1");
    apiSample.setTemplateId(createdTemplate.getId());
    ApiSampleWithFullSubSamples createdSample =
        sampleApiMgr.createNewApiSample(apiSample, testUser);
    assertNotNull(createdSample);
    assertEquals(createdTemplate.getId(), createdSample.getTemplateId());
    assertEquals(1, createdSample.getTemplateVersion());
    assertEquals(2, createdSample.getFields().size());
    assertEquals("my radio", createdSample.getFields().get(0).getName());
    assertEquals(
        List.of("r1", "r2", "r3"), createdSample.getFields().get(0).getDefinition().getOptions());
    assertEquals(List.of("r1"), createdSample.getFields().get(0).getSelectedOptions());

    // update template
    ApiSampleTemplate templateUpdates = new ApiSampleTemplate();
    templateUpdates.setId(createdTemplate.getId());
    templateUpdates.setName("test template updated");
    // add a new option to radio field, and set it as a default
    ApiInventoryEntityField radioFieldUpdates = new ApiInventoryEntityField();
    radioFieldUpdates.setId(createdTemplate.getFields().get(0).getId());
    radioFieldUpdates.setName("updated radio");
    ApiInventoryFieldDef updatedRadioDef =
        new ApiInventoryFieldDef(List.of("r2", "r3", "r4"), false);
    radioFieldUpdates.setDefinition(updatedRadioDef);
    templateUpdates.getFields().add(radioFieldUpdates);

    // update the template
    ApiSampleTemplate updatedTemplate =
        sampleApiMgr.updateApiSampleTemplate(templateUpdates, testUser);
    assertNotNull(updatedTemplate);
    assertEquals(2, updatedTemplate.getVersion());
    assertEquals("test template updated", updatedTemplate.getName());
    assertEquals(2, updatedTemplate.getFields().size());
    // option removed from definition is also removed from selection
    assertEquals(Collections.emptyList(), updatedTemplate.getFields().get(0).getSelectedOptions());

    // retrieve sample
    ApiSample retrievedSample = sampleApiMgr.getApiSampleById(createdSample.getId(), testUser);
    assertEquals(createdTemplate.getId(), retrievedSample.getTemplateId());
    assertEquals(1, retrievedSample.getTemplateVersion());

    // try updating sample to latest template
    IllegalStateException iae =
        assertThrows(
            IllegalStateException.class,
            () ->
                sampleApiMgr.updateSampleToLatestTemplateVersion(createdSample.getId(), testUser));
    assertEquals(
        "Field [updated radio] value [r1] is invalid according to latest template field definition",
        iae.getMessage());

    // update sample's choice field value that is blocking the update
    ApiSample sampleUpdates = new ApiSample();
    sampleUpdates.setId(retrievedSample.getId());
    // add a new option to radio field, and set it as a default
    ApiInventoryEntityField radioFieldUpdate = new ApiInventoryEntityField();
    radioFieldUpdate.setId(retrievedSample.getFields().get(0).getId());
    radioFieldUpdate.setSelectedOptions(List.of("r2"));
    sampleUpdates.setFields(Collections.singletonList(radioFieldUpdate));
    sampleApiMgr.updateApiSample(sampleUpdates, testUser);

    // try updating to latest template again
    sampleApiMgr.updateSampleToLatestTemplateVersion(createdSample.getId(), testUser);

    // check the sample
    ApiSample updatedSample =
        sampleApiMgr.updateSampleToLatestTemplateVersion(createdSample.getId(), testUser);
    assertEquals(createdTemplate.getId(), updatedSample.getTemplateId());
    assertEquals(2, updatedSample.getTemplateVersion());
    assertEquals(2, updatedSample.getFields().size());
    assertEquals("updated radio", updatedSample.getFields().get(0).getName());
    assertEquals(
        List.of("r2", "r3", "r4"), updatedSample.getFields().get(0).getDefinition().getOptions());
    assertEquals(List.of("r2"), updatedSample.getFields().get(0).getSelectedOptions());
  }

  @Test
  public void updateSampleToLatestVersion_modifyChoiceOptions() throws InterruptedException {

    // prepare new template
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("test template with radio and choice");
    // add number field
    ApiInventoryEntityField numberField =
        createBasicApiSampleField("my number", ApiFieldType.NUMBER, "3.14");
    sampleTemplatePost.getFields().add(numberField);
    // add choice field
    ApiInventoryEntityField choiceField =
        createBasicApiSampleOptionsField("my choice", ApiFieldType.CHOICE, List.of("c1", "c2"));
    ApiInventoryFieldDef def = new ApiInventoryFieldDef(List.of("c1", "c2", "c3"), true);
    choiceField.setDefinition(def);
    sampleTemplatePost.getFields().add(choiceField);

    // create template fine
    ApiSampleTemplate createdTemplate =
        sampleApiMgr.createSampleTemplate(sampleTemplatePost, testUser);
    assertEquals(2, createdTemplate.getFields().size());

    // retrieve the template
    ApiSample retrievedTemplate =
        sampleApiMgr.getApiSampleTemplateById(createdTemplate.getId(), testUser);
    assertEquals(2, retrievedTemplate.getFields().size());
    assertEquals("my number", retrievedTemplate.getFields().get(0).getName());
    ApiInventoryEntityField retrievedTemplateChoiceField = retrievedTemplate.getFields().get(1);
    assertEquals("my choice", retrievedTemplateChoiceField.getName());
    assertEquals(
        List.of("c1", "c2", "c3"), retrievedTemplateChoiceField.getDefinition().getOptions());
    assertEquals(List.of("c1", "c2"), retrievedTemplateChoiceField.getSelectedOptions());

    // create sample from template
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample from template v1");
    apiSample.setTemplateId(retrievedTemplate.getId());
    ApiSampleWithFullSubSamples createdSample1 =
        sampleApiMgr.createNewApiSample(apiSample, testUser);
    assertNotNull(createdSample1);
    // retrieve the sample - field definition should come from template
    ApiSample retrievedSample1 = sampleApiMgr.getApiSampleById(createdSample1.getId(), testUser);
    assertEquals(retrievedTemplate.getId(), retrievedSample1.getTemplateId());
    assertEquals(2, retrievedSample1.getFields().size());
    assertEquals("my number", retrievedSample1.getFields().get(0).getName());
    assertEquals("my choice", retrievedSample1.getFields().get(1).getName());
    assertEquals(null, retrievedSample1.getFields().get(1).getContent());
    assertEquals(
        List.of("c1", "c2", "c3"),
        retrievedSample1.getFields().get(1).getDefinition().getOptions());
    assertEquals(List.of("c1", "c2"), retrievedSample1.getFields().get(1).getSelectedOptions());

    // update template
    ApiSampleTemplate templateUpdates = new ApiSampleTemplate();
    templateUpdates.setId(retrievedTemplate.getId());
    // add a new option to choice field, and set it as a default
    ApiInventoryEntityField choiceFieldUpdates = new ApiInventoryEntityField();
    choiceFieldUpdates.setId(retrievedTemplateChoiceField.getId());
    choiceFieldUpdates.setName("updated choice");
    ApiInventoryFieldDef updatedChoiceDef =
        new ApiInventoryFieldDef(List.of("c2", "c3", "c4"), false);
    choiceFieldUpdates.setDefinition(updatedChoiceDef);
    templateUpdates.getFields().add(choiceFieldUpdates);

    // update the template
    ApiSampleTemplate updatedTemplate =
        sampleApiMgr.updateApiSampleTemplate(templateUpdates, testUser);
    assertNotNull(updatedTemplate);
    assertEquals(retrievedTemplate.getGlobalId(), updatedTemplate.getGlobalId());
    assertEquals(2, updatedTemplate.getVersion());
    assertEquals(2, updatedTemplate.getFields().size());
    assertEquals("my number", updatedTemplate.getFields().get(0).getName());
    retrievedTemplateChoiceField = updatedTemplate.getFields().get(1);
    assertEquals("updated choice", retrievedTemplateChoiceField.getName());
    assertEquals(
        List.of("c2", "c3", "c4"), retrievedTemplateChoiceField.getDefinition().getOptions());
    // option removed from definition is no longer in selection
    assertEquals(List.of("c2"), retrievedTemplateChoiceField.getSelectedOptions());

    // create sample from updated template
    ApiSampleWithFullSubSamples apiSample2 =
        new ApiSampleWithFullSubSamples("sample from template v2");
    apiSample2.setTemplateId(retrievedTemplate.getId());
    ApiSampleWithFullSubSamples createdSample2 =
        sampleApiMgr.createNewApiSample(apiSample2, testUser);
    assertNotNull(createdSample2);
    ApiSample retrievedSample2 = sampleApiMgr.getApiSampleById(createdSample2.getId(), testUser);
    assertEquals(2, retrievedSample2.getFields().size());
    assertEquals("my number", retrievedSample2.getFields().get(0).getName());
    assertEquals("updated choice", retrievedSample2.getFields().get(1).getName());
    assertEquals(null, retrievedSample2.getFields().get(1).getContent());
    assertEquals(
        List.of("c2", "c3", "c4"),
        retrievedSample2.getFields().get(1).getDefinition().getOptions());
    assertEquals(List.of("c2"), retrievedSample2.getFields().get(1).getSelectedOptions());

    // first sample still holds the previous field definitions
    retrievedSample1 = sampleApiMgr.getApiSampleById(createdSample1.getId(), testUser);
    assertEquals(retrievedTemplate.getId(), retrievedSample1.getTemplateId());
    assertEquals(1, retrievedSample1.getTemplateVersion());
    assertEquals(2, retrievedSample1.getFields().size());
    assertEquals("my number", retrievedSample1.getFields().get(0).getName());
    assertEquals("my choice", retrievedSample1.getFields().get(1).getName());
    assertEquals(null, retrievedSample1.getFields().get(1).getContent());
    assertEquals(List.of("c1", "c2"), retrievedSample1.getFields().get(1).getSelectedOptions());
    assertEquals(
        List.of("c1", "c2", "c3"),
        retrievedSample1.getFields().get(1).getDefinition().getOptions());

    // try updating first sample to latest template version
    IllegalStateException iae =
        assertThrows(
            IllegalStateException.class,
            () ->
                sampleApiMgr.updateSampleToLatestTemplateVersion(createdSample1.getId(), testUser));
    assertEquals(
        "Field [updated choice] value [[\"c1\",\"c2\"]] is invalid according to latest template"
            + " field definition",
        iae.getMessage());

    // update sample's choice field value that is blocking the update
    ApiSample sampleUpdates = new ApiSample();
    sampleUpdates.setId(createdSample1.getId());
    // add a new option to radio field, and set it as a default
    ApiInventoryEntityField choiceFieldUpdate = new ApiInventoryEntityField();
    choiceFieldUpdate.setId(retrievedSample1.getFields().get(1).getId());
    choiceFieldUpdate.setSelectedOptions(List.of("c2", "c3"));
    sampleUpdates.setFields(Collections.singletonList(choiceFieldUpdate));
    sampleUpdates.setDescription("updated description");
    sampleApiMgr.updateApiSample(sampleUpdates, testUser);

    // try updating to latest template version again
    sampleApiMgr.updateSampleToLatestTemplateVersion(createdSample1.getId(), testUser);

    // sample now points to new definition
    retrievedSample1 = sampleApiMgr.getApiSampleById(createdSample1.getId(), testUser);
    assertEquals(retrievedTemplate.getId(), retrievedSample1.getTemplateId());
    assertEquals(2, retrievedSample1.getTemplateVersion());
    assertEquals(2, retrievedSample1.getFields().size());
    assertEquals("my number", retrievedSample1.getFields().get(0).getName());
    assertEquals("updated choice", retrievedSample1.getFields().get(1).getName());
    assertEquals(null, retrievedSample1.getFields().get(1).getContent());
    assertEquals(List.of("c2", "c3"), retrievedSample1.getFields().get(1).getSelectedOptions());
    assertEquals(
        List.of("c2", "c3", "c4"),
        retrievedSample1.getFields().get(1).getDefinition().getOptions());
  }

  @Test
  public void updateToLatestTemplateVersionRejectsSampleWithNoTemplate() {
    // a sample created without a template has a null parent template id; updating it to the latest
    // template version must surface a clear error rather than a not-found for template id "null"
    ApiSampleWithFullSubSamples apiSample =
        new ApiSampleWithFullSubSamples("sample without a template");
    ApiSampleWithFullSubSamples createdSample =
        sampleApiMgr.createNewApiSample(apiSample, testUser);

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                sampleApiMgr.updateSampleToLatestTemplateVersion(createdSample.getId(), testUser));
    assertEquals("Sample is not based on any template", iae.getMessage());
  }
}
