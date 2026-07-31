package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiFieldToModelFieldFactory;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.inventory.field.InventoryTextField;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Applying a structured link-field value must not leak InventoryLink rows: an unchanged payload is
 * a no-op, a changed payload updates the existing row in place (which also revalidates the target
 * and recaptures the pinned audit revision), and clearing the value soft-deletes the old row
 * through the manager. Creating a fresh row on every save left the previous row in the DB with
 * {@code deleted=false} and nothing pointing at it.
 *
 * <p>Likewise, soft-deleting a structured link field (a template link-field delete, or its
 * propagation to child samples via {@code Sample#updateToLatestTemplateVersion}) must soft-delete
 * the field's link, otherwise the link row lingers with {@code deleted=false} after the field is
 * gone.
 */
@ExtendWith(MockitoExtension.class)
class SampleApiManagerImplLinkFieldTest {

  @Mock private InventoryLinkManager inventoryLinkManager;
  private SampleApiManagerImpl manager;

  private User user;
  private InventoryLinkField dbField;
  private InventoryLink dbLink;

  @BeforeEach
  void setUp() {
    manager = new SampleApiManagerImpl();
    // the manager is field-autowired in production; wire the mock in directly
    ReflectionTestUtils.setField(manager, "inventoryLinkManager", inventoryLinkManager);
    // the real factory is stateless, so the template-create path is exercised end to end
    ReflectionTestUtils.setField(
        manager, "apiFieldToModelFieldFactory", new ApiFieldToModelFieldFactory());
    user = TestFactory.createAnyUser("any");
    dbLink = new InventoryLink();
    dbLink.setRelationType("References");
    dbLink.setTargetGlobalId("SA2");
    dbLink.setTargetPrefix(GlobalIdPrefix.SA);
    dbLink.setTargetDbId(2L);
    dbField = new InventoryLinkField();
    dbField.setName("related sample");
    dbField.setLink(dbLink);
  }

  private ApiInventoryEntityField apiLinkField(
      String targetGlobalId, String relationType, Long versionPin) {
    ApiInventoryEntityField apiField = new ApiInventoryEntityField();
    ApiInventoryLink apiLink = new ApiInventoryLink();
    apiLink.setTargetGlobalId(targetGlobalId);
    apiLink.setRelationType(relationType);
    apiLink.setVersionPin(versionPin);
    apiField.setLink(apiLink);
    return apiField;
  }

  @Test
  void unchangedLinkLeavesTheExistingRowAlone() {
    ApiInventoryEntityField apiField = apiLinkField("SA2", "References", null);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertFalse(changed);
    assertSame(dbLink, dbField.getLink());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void pinnedSuffixedTargetMatchingStoredBaseIdIsUnchanged() {
    // the stored row holds the base id ("SA2") with the pin in versionPin; an
    // incoming suffixed id ("SA2v4") carrying the same effective pin must
    // compare equal, not fire a spurious update (and Envers revision) on
    // every save
    dbLink.setVersionPin(4L);
    ApiInventoryEntityField apiField = apiLinkField("SA2v4", "References", null);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertFalse(changed);
    assertSame(dbLink, dbField.getLink());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void retargetUpdatesTheExistingRowInPlace() {
    ApiInventoryEntityField apiField = apiLinkField("SA3", "References", null);
    when(inventoryLinkManager.updateLink(dbLink, apiField.getLink(), user)).thenReturn(dbLink);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    verify(inventoryLinkManager).updateLink(dbLink, apiField.getLink(), user);
    verify(inventoryLinkManager, never()).createLink(any(), any());
  }

  @Test
  void versionPinChangeUpdatesTheExistingRowInPlace() {
    ApiInventoryEntityField apiField = apiLinkField("SA2", "References", 4L);
    when(inventoryLinkManager.updateLink(dbLink, apiField.getLink(), user)).thenReturn(dbLink);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    verify(inventoryLinkManager).updateLink(dbLink, apiField.getLink(), user);
  }

  @Test
  void relationTypeChangeUpdatesTheExistingRowInPlace() {
    ApiInventoryEntityField apiField = apiLinkField("SA2", "IsCitedBy", null);
    when(inventoryLinkManager.updateLink(dbLink, apiField.getLink(), user)).thenReturn(dbLink);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    verify(inventoryLinkManager).updateLink(dbLink, apiField.getLink(), user);
  }

  @Test
  void clearingTheValueDereferencesTheRowForOrphanRemoval() {
    ApiInventoryEntityField apiField = new ApiInventoryEntityField();
    // no link payload at all: the field's value is being cleared. The field's
    // orphanRemoval mapping hard-deletes the dereferenced row at flush (with a
    // DEL revision in InventoryLink_AUD); an extra soft-delete write would be
    // collapsed away by Envers and is deliberately not attempted.
    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    assertNull(dbField.getLink());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void invalidRelationTypeIsRejectedWithCleanError() {
    ApiInventoryEntityField apiField = apiLinkField("SA3", "NotARelation", null);

    com.researchspace.api.v1.auth.ApiRuntimeException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            com.researchspace.api.v1.auth.ApiRuntimeException.class,
            () -> manager.applyLinkFieldValue(dbField, apiField, user));
    org.junit.jupiter.api.Assertions.assertEquals(
        "errors.inventory.field.link.relationTypeInvalid", ex.getErrorCode());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void relationOutsideTemplateWhitelistIsRejected() {
    dbField.setAllowedRelationTypes("References|IsPartOf");
    ApiInventoryEntityField apiField = apiLinkField("SA3", "Cites", null);

    com.researchspace.api.v1.auth.ApiRuntimeException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            com.researchspace.api.v1.auth.ApiRuntimeException.class,
            () -> manager.applyLinkFieldValue(dbField, apiField, user));
    org.junit.jupiter.api.Assertions.assertEquals(
        "errors.inventory.field.link.relationTypeNotPermitted", ex.getErrorCode());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void relationInsideTemplateWhitelistIsAccepted() {
    dbField.setAllowedRelationTypes("References|IsPartOf");
    ApiInventoryEntityField apiField = apiLinkField("SA3", "IsPartOf", null);
    when(inventoryLinkManager.updateLink(dbLink, apiField.getLink(), user)).thenReturn(dbLink);

    assertTrue(manager.applyLinkFieldValue(dbField, apiField, user));
  }

  @Test
  void clearingAnAlreadyEmptyFieldIsANoop() {
    dbField.setLink(null);
    ApiInventoryEntityField apiField = new ApiInventoryEntityField();

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertFalse(changed);
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void anEmptyFieldGainsItsFirstLinkViaCreate() {
    dbField.setLink(null);
    InventoryLink created = new InventoryLink();
    ApiInventoryEntityField apiField = apiLinkField("SA3", "References", null);
    when(inventoryLinkManager.createLink(apiField.getLink(), user)).thenReturn(created);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    assertSame(created, dbField.getLink());
    verify(inventoryLinkManager, never()).updateLink(any(), any(), any());
  }

  @Test
  void existingSampleLinkFieldAcquiresTemplatesUpdatedWhitelist() {
    // RSDEV-1200: when an existing sample is synced to a newer template version, the model's
    // per-field sync copies name/columnIndex/mandatory/deletion but not the link whitelist, so
    // the sample kept the whitelist captured at creation. (New samples are fine: they clone the
    // template field via shallowCopy(), which copies the whitelist.)
    InventoryLinkField templateField = new InventoryLinkField();
    templateField.setAllowedRelationTypes("IsCitedBy|Cites");
    InventoryLinkField sampleField = new InventoryLinkField();
    sampleField.setAllowedRelationTypes("References");
    sampleField.setTemplateField(templateField);

    List<InventoryEntityField> sampleFields = List.of(sampleField);
    assertTrue(InventoryApiManagerImpl.syncLinkFieldWhitelistsFromTemplate(sampleFields));
    assertEquals("IsCitedBy|Cites", sampleField.getAllowedRelationTypes());
  }

  @Test
  void clearingTheTemplateWhitelistClearsItOnExistingSampleLinkField() {
    // template whitelist removed (back to "all" relation types == null): the sample must follow
    InventoryLinkField templateField = new InventoryLinkField();
    templateField.setAllowedRelationTypes(null);
    InventoryLinkField sampleField = new InventoryLinkField();
    sampleField.setAllowedRelationTypes("References");
    sampleField.setTemplateField(templateField);

    List<InventoryEntityField> sampleFields = List.of(sampleField);
    assertTrue(InventoryApiManagerImpl.syncLinkFieldWhitelistsFromTemplate(sampleFields));
    assertNull(sampleField.getAllowedRelationTypes());
  }

  @Test
  void unchangedTemplateWhitelistIsANoop() {
    InventoryLinkField templateField = new InventoryLinkField();
    templateField.setAllowedRelationTypes("References|IsDerivedFrom");
    InventoryLinkField sampleField = new InventoryLinkField();
    sampleField.setAllowedRelationTypes("References|IsDerivedFrom");
    sampleField.setTemplateField(templateField);

    List<InventoryEntityField> sampleFields = List.of(sampleField);
    assertFalse(InventoryApiManagerImpl.syncLinkFieldWhitelistsFromTemplate(sampleFields));
    assertEquals("References|IsDerivedFrom", sampleField.getAllowedRelationTypes());
  }

  @Test
  void nonLinkAndTemplatelessFieldsAreLeftUntouched() {
    // a non-link field and a link field with no connected template field must be skipped (no NPE)
    InventoryTextField textField = new InventoryTextField("notes");
    InventoryLinkField orphanLinkField = new InventoryLinkField();
    orphanLinkField.setAllowedRelationTypes("References");

    List<InventoryEntityField> sampleFields = List.of(textField, orphanLinkField);
    assertFalse(InventoryApiManagerImpl.syncLinkFieldWhitelistsFromTemplate(sampleFields));
    assertEquals("References", orphanLinkField.getAllowedRelationTypes());
  }

  @Test
  void aNewTemplateLinkFieldIsCreatedWithItsDefaultLink() {
    // RSDEV-1246: a template's Link field may carry a default link, stored in the same link_id an
    // item's link uses so that shallowCopy() stamps it onto items with no extra copy code. The
    // stateless factory only sets the whitelist, so the manager must apply the default through
    // InventoryLinkManager (which validates the target and captures the Envers revision).
    ApiSampleTemplatePost apiTemplate = new ApiSampleTemplatePost();
    ApiInventoryEntityField apiField = apiLinkField("SA2", "References", null);
    apiField.setType(ApiFieldType.LINK);
    apiField.setName("default related sample");
    apiTemplate.setFields(List.of(apiField));
    InventoryLink created = new InventoryLink();
    when(inventoryLinkManager.createLink(apiField.getLink(), user)).thenReturn(created);

    SampleTemplate dbTemplate = new SampleTemplate();
    manager.createFields(apiTemplate, dbTemplate, user);

    InventoryLinkField added = (InventoryLinkField) dbTemplate.getActiveFields().get(0);
    assertSame(created, added.getLink());
  }

  @Test
  void aLinkFieldAddedToAnExistingTemplateIsCreatedWithItsDefaultLink() {
    // the second create path: a new-field-request on an existing template must stamp the default
    // too, otherwise a default set when the field is added is silently dropped
    ApiSampleTemplate apiTemplate = new ApiSampleTemplate();
    ApiInventoryEntityField apiField = apiLinkField("SA2", "References", null);
    apiField.setType(ApiFieldType.LINK);
    apiField.setName("default related sample");
    apiField.setNewFieldRequest(true);
    apiTemplate.setFields(List.of(apiField));
    InventoryLink created = new InventoryLink();
    when(inventoryLinkManager.createLink(apiField.getLink(), user)).thenReturn(created);

    SampleTemplate dbTemplate = new SampleTemplate();
    assertTrue(
        manager.createDeleteRequestedFieldsInDbSampleTemplate(apiTemplate, dbTemplate, user));

    InventoryLinkField added = (InventoryLinkField) dbTemplate.getActiveFields().get(0);
    assertSame(created, added.getLink());
  }

  @Test
  void editingATemplatesDefaultLinkUpdatesItsRowInPlace() {
    // updateDbSample used to skip the link write path for templates, so a template's default was
    // read back on GET but never written on PUT. Templates now go through the same path as items.
    ApiSampleTemplate apiTemplate = new ApiSampleTemplate();
    ApiInventoryEntityField apiField = apiLinkField("SA3", "References", null);
    apiField.setId(7L);
    apiTemplate.setFields(List.of(apiField));
    when(inventoryLinkManager.updateLink(dbLink, apiField.getLink(), user)).thenReturn(dbLink);

    SampleTemplate dbTemplate = new SampleTemplate();
    dbTemplate.setId(1L);
    dbField.setId(7L);
    dbTemplate.addSampleField(dbField);

    assertTrue(manager.applyLinkFieldValuesOnUpdate(apiTemplate, dbTemplate, user));
    verify(inventoryLinkManager).updateLink(dbLink, apiField.getLink(), user);
  }

  @Test
  void aTemplateCannotDefaultToItself() {
    ApiSampleTemplate apiTemplate = new ApiSampleTemplate();
    ApiInventoryEntityField apiField = apiLinkField("IT1", "References", null);
    apiField.setId(7L);
    apiTemplate.setFields(List.of(apiField));

    SampleTemplate dbTemplate = new SampleTemplate();
    dbTemplate.setId(1L);
    dbField.setId(7L);
    dbTemplate.addSampleField(dbField);

    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class,
            () -> manager.applyLinkFieldValuesOnUpdate(apiTemplate, dbTemplate, user));
    assertEquals("errors.inventory.field.link.selfLinkForbidden", ex.getErrorCode());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void narrowingTheWhitelistPastTheFieldsOwnDefaultIsRejected() {
    // an unchanged default is a no-op on the per-link path, so a whitelist-only edit would
    // otherwise leave the template holding a default its own field forbids
    dbField.setAllowedRelationTypes("IsPartOf");

    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class,
            () -> InventoryApiManagerImpl.assertDefaultLinksMatchWhitelists(List.of(dbField)));
    assertEquals("errors.inventory.field.link.defaultRelationTypeNotPermitted", ex.getErrorCode());
  }

  @Test
  void aDefaultLinkStillInsideTheWhitelistIsAccepted() {
    dbField.setAllowedRelationTypes("References|IsPartOf");

    InventoryApiManagerImpl.assertDefaultLinksMatchWhitelists(List.of(dbField));
  }

  @Test
  void aLinkFieldWithNoDefaultIsUnaffectedByAnyWhitelist() {
    dbField.setLink(null);
    dbField.setAllowedRelationTypes("IsPartOf");

    InventoryApiManagerImpl.assertDefaultLinksMatchWhitelists(
        List.of(dbField, new InventoryTextField("notes")));
  }

  @Test
  void deletingALinkFieldSoftDeletesItsLinkThroughTheManager() {
    dbField.setDeleted(true);

    manager.softDeleteLinkOfDeletedLinkField(dbField, user);

    verify(inventoryLinkManager).deleteLink(dbLink, user);
  }

  @Test
  void aDeletedLinkFieldWithNoLinkLeavesTheManagerUntouched() {
    dbField.setLink(null);
    dbField.setDeleted(true);

    manager.softDeleteLinkOfDeletedLinkField(dbField, user);

    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void aDeletedLinkFieldWhoseLinkIsAlreadyDeletedLeavesTheManagerUntouched() {
    dbLink.setDeleted(true);
    dbField.setDeleted(true);

    manager.softDeleteLinkOfDeletedLinkField(dbField, user);

    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void aLiveLinkFieldIsLeftAlone() {
    // field not deleted (default): a live field keeps its link
    manager.softDeleteLinkOfDeletedLinkField(dbField, user);

    verifyNoInteractions(inventoryLinkManager);
  }
}
