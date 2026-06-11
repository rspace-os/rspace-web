package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.testutils.TestFactory;
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
