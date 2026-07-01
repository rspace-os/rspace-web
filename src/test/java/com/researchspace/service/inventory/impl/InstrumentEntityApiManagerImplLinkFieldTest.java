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

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    assertNull(dbField.getLink());
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
}
