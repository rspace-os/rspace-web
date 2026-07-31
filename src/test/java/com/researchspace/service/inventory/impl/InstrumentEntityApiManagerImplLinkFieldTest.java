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
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
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
 * Unit tests for the link-field persistence logic in {@link InstrumentEntityApiManagerImpl}.
 *
 * <p>The {@code applyLinkFieldValue} method is the creation/update hot-path for structured
 * link-type template fields on instruments. It mirrors the equivalent in {@link
 * SampleApiManagerImpl}; these tests guard against independent drift or regression.
 */
@ExtendWith(MockitoExtension.class)
class InstrumentEntityApiManagerImplLinkFieldTest {

  @Mock private InventoryLinkManager inventoryLinkManager;
  private InstrumentEntityApiManagerImpl manager;

  private User user;
  private InventoryLinkField dbField;
  private InventoryLink dbLink;

  @BeforeEach
  void setUp() {
    manager = new InstrumentEntityApiManagerImpl();
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
    apiField.setLink(null);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    assertNull(dbField.getLink());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void anItemUpdateThatOmitsTheLinkEntirelyStillClearsIt() {
    // RSDEV-1131 semantics, which InstrumentEntityApiManagerTest.linkFieldValue_clearedWhenInstrume
    // ntUpdated pins: an instrument's field list arrives complete, so a link field carrying no link
    // at all means the user cleared it. Only a TEMPLATE's field list can be partial.
    ApiInventoryEntityField apiField = new ApiInventoryEntityField();

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    assertNull(dbField.getLink());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void aTemplatePutThatOmitsTheLinkKeepsTheDefault() {
    // the whitelist-only template edit: {"fields":[{"id":N,"allowedRelationTypes":[...]}]}. Absence
    // there cannot mean "clear", or a legitimate partial edit would destroy the default link.
    ApiInventoryEntityField apiField = new ApiInventoryEntityField();

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user, true);

    assertFalse(changed);
    assertSame(dbLink, dbField.getLink());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void invalidRelationTypeIsRejectedWithCleanError() {
    ApiInventoryEntityField apiField = apiLinkField("SA3", "NotARelation", null);

    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class, () -> manager.applyLinkFieldValue(dbField, apiField, user));
    assertEquals("errors.inventory.field.link.relationTypeInvalid", ex.getErrorCode());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void relationOutsideTemplateWhitelistIsRejected() {
    dbField.setAllowedRelationTypes("References|IsPartOf");
    ApiInventoryEntityField apiField = apiLinkField("SA3", "Cites", null);

    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class, () -> manager.applyLinkFieldValue(dbField, apiField, user));
    assertEquals("errors.inventory.field.link.relationTypeNotPermitted", ex.getErrorCode());
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
  void deletingAnInstrumentLinkFieldSoftDeletesItsLink() {
    // RSDEV-1270: the instrument manager had no equivalent of the sample manager's reconciliation,
    // so a deleted link field left its InventoryLink row with deleted=false
    dbField.setDeleted(true);

    manager.softDeleteLinkOfDeletedLinkField(dbField, user);

    verify(inventoryLinkManager).deleteLink(dbLink, user);
  }

  @Test
  void aDeletedInstrumentLinkFieldWhoseLinkIsAlreadyDeletedIsLeftAlone() {
    dbLink.setDeleted(true);
    dbField.setDeleted(true);

    manager.softDeleteLinkOfDeletedLinkField(dbField, user);

    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void aLiveInstrumentLinkFieldKeepsItsLink() {
    manager.softDeleteLinkOfDeletedLinkField(dbField, user);

    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void aNewInstrumentTemplateLinkFieldIsCreatedWithItsDefaultLink() {
    // RSDEV-1246: mirrors the sample template create path
    ApiInstrumentTemplatePost post = new ApiInstrumentTemplatePost();
    ApiInventoryEntityField apiField = apiLinkField("SA2", "References", null);
    apiField.setType(ApiFieldType.LINK);
    apiField.setName("default related sample");
    post.setFields(List.of(apiField));
    InventoryLink created = new InventoryLink();
    when(inventoryLinkManager.createLink(apiField.getLink(), user)).thenReturn(created);

    InstrumentTemplate dbTemplate = new InstrumentTemplate();
    manager.addFieldsToNewInstrumentTemplate(post, dbTemplate, user);

    InventoryLinkField added = (InventoryLinkField) dbTemplate.getActiveFields().get(0);
    assertSame(created, added.getLink());
  }

  @Test
  void aLinkFieldAddedToAnExistingInstrumentTemplateIsCreatedWithItsDefaultLink() {
    ApiInstrumentTemplate apiTemplate = new ApiInstrumentTemplate();
    ApiInventoryEntityField apiField = apiLinkField("SA2", "References", null);
    apiField.setType(ApiFieldType.LINK);
    apiField.setName("default related sample");
    apiField.setNewFieldRequest(true);
    apiTemplate.setFields(List.of(apiField));
    InventoryLink created = new InventoryLink();
    when(inventoryLinkManager.createLink(apiField.getLink(), user)).thenReturn(created);

    InstrumentTemplate dbTemplate = new InstrumentTemplate();
    assertTrue(
        manager.createDeleteRequestedFieldsInDbInstrumentTemplate(apiTemplate, dbTemplate, user));

    InventoryLinkField added = (InventoryLinkField) dbTemplate.getActiveFields().get(0);
    assertSame(created, added.getLink());
  }

  @Test
  void editingAnInstrumentTemplatesDefaultLinkUpdatesItsRowInPlace() {
    ApiInstrumentTemplate apiTemplate = new ApiInstrumentTemplate();
    ApiInventoryEntityField apiField = apiLinkField("SA3", "References", null);
    apiField.setId(7L);
    apiTemplate.setFields(List.of(apiField));
    when(inventoryLinkManager.updateLink(dbLink, apiField.getLink(), user)).thenReturn(dbLink);

    InstrumentTemplate dbTemplate = new InstrumentTemplate();
    dbTemplate.setId(1L);
    dbField.setId(7L);
    dbField.setInventoryRecord(dbTemplate);
    dbTemplate.getFields().add(dbField);
    dbTemplate.refreshActiveFieldsAndColumnIndex();

    assertTrue(manager.applyLinkFieldValuesOnUpdate(apiTemplate, dbTemplate, user));
    verify(inventoryLinkManager).updateLink(dbLink, apiField.getLink(), user);
  }
}
