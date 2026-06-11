package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.testutils.TestFactory;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The DTO apply loop treats an existing link's target as immutable, so target (and version pin)
 * changes must be applied through the service-layer {@link InventoryLinkManager}, which validates
 * existence/readability and recaptures the audit revision. Without this, editing a link to a
 * different target saved successfully but silently kept the previous target.
 */
@ExtendWith(MockitoExtension.class)
class ApiExtraFieldsHelperLinkUpdateTest {

  @Mock private InventoryLinkManager inventoryLinkManager;
  private ApiExtraFieldsHelper helper;

  private User user;
  private ExtraLinkField dbField;
  private InventoryLink dbLink;

  @BeforeEach
  void setUp() {
    helper = new ApiExtraFieldsHelper(new RecordFactory());
    // the manager is field-autowired in production; wire the mock in directly
    ReflectionTestUtils.setField(helper, "inventoryLinkManager", inventoryLinkManager);
    user = TestFactory.createAnyUser("any");
    dbLink = new InventoryLink();
    dbLink.setRelationType("References");
    dbLink.setTargetGlobalId("SA2");
    dbLink.setTargetPrefix(GlobalIdPrefix.SA);
    dbLink.setTargetDbId(2L);
    dbField = new ExtraLinkField();
    dbField.setId(5L);
    dbField.setLink(dbLink);
  }

  private ApiExtraField incomingLinkField(Long id, String targetGlobalId, Long versionPin) {
    ApiExtraField apiField = new ApiExtraField(ExtraFieldTypeEnum.LINK);
    apiField.setId(id);
    ApiInventoryLink apiLink = new ApiInventoryLink();
    apiLink.setRelationType("References");
    apiLink.setTargetGlobalId(targetGlobalId);
    apiLink.setVersionPin(versionPin);
    apiField.setLink(apiLink);
    return apiField;
  }

  @Test
  void retargetIsAppliedThroughTheLinkManager() {
    ApiExtraField apiField = incomingLinkField(5L, "SA3", null);

    boolean changed =
        helper.applyExistingLinkFieldChanges(List.of(apiField), List.of(dbField), user);

    assertTrue(changed);
    verify(inventoryLinkManager).updateLink(dbLink, apiField.getLink(), user);
  }

  @Test
  void versionPinChangeIsAppliedThroughTheLinkManagerSoTheRevisionIsRecaptured() {
    ApiExtraField apiField = incomingLinkField(5L, "SA2", 4L);

    boolean changed =
        helper.applyExistingLinkFieldChanges(List.of(apiField), List.of(dbField), user);

    assertTrue(changed);
    verify(inventoryLinkManager).updateLink(dbLink, apiField.getLink(), user);
  }

  @Test
  void unchangedLinkIsLeftAlone() {
    ApiExtraField apiField = incomingLinkField(5L, "SA2", null);

    boolean changed =
        helper.applyExistingLinkFieldChanges(List.of(apiField), List.of(dbField), user);

    assertFalse(changed);
    verify(inventoryLinkManager, never()).updateLink(any(), any(), any());
  }

  @Test
  void anEmptyLinkFieldGainsItsFirstLinkViaCreate() {
    dbField.setLink(null);
    InventoryLink created = new InventoryLink();
    ApiExtraField apiField = incomingLinkField(5L, "SA3", null);
    when(inventoryLinkManager.createLink(apiField.getLink(), user)).thenReturn(created);

    boolean changed =
        helper.applyExistingLinkFieldChanges(List.of(apiField), List.of(dbField), user);

    assertTrue(changed);
    assertSame(created, dbField.getLink());
  }

  @Test
  void newAndDeleteFlaggedFieldsAndBlankTargetsAreSkipped() {
    ApiExtraField newField = incomingLinkField(null, "SA3", null);
    newField.setNewFieldRequest(true);
    ApiExtraField deleteField = incomingLinkField(5L, "SA3", null);
    deleteField.setDeleteFieldRequest(true);
    ApiExtraField blankTarget = incomingLinkField(5L, "", null);

    boolean changed =
        helper.applyExistingLinkFieldChanges(
            List.of(newField, deleteField, blankTarget), List.of(dbField), user);

    assertFalse(changed);
    verify(inventoryLinkManager, never()).updateLink(any(), any(), any());
    verify(inventoryLinkManager, never()).createLink(any(), any());
  }

  @Test
  void selfLinkViaUpdatePathIsRejected() {
    // the controller-layer validator can be bypassed by omitting "type", so
    // the service-layer apply must enforce no-self-links itself
    ExtraLinkField selfField = org.mockito.Mockito.mock(ExtraLinkField.class);
    org.mockito.Mockito.lenient().when(selfField.getId()).thenReturn(5L);
    org.mockito.Mockito.lenient().when(selfField.getLink()).thenReturn(dbLink);
    org.mockito.Mockito.lenient()
        .when(selfField.getConnectedRecordGlobalIdentifier())
        .thenReturn("SA9");
    ApiExtraField apiField = incomingLinkField(5L, "SA9", null);

    org.junit.jupiter.api.Assertions.assertThrows(
        com.researchspace.api.v1.auth.ApiRuntimeException.class,
        () -> helper.applyExistingLinkFieldChanges(List.of(apiField), List.of(selfField), user));
    verify(inventoryLinkManager, never()).updateLink(any(), any(), any());
    verify(inventoryLinkManager, never()).createLink(any(), any());
  }

  @Test
  void suffixPinnedTargetEqualToStoredPinIsNotASpuriousUpdate() {
    // a raw client may pin via the "vN" suffix with versionPin null; that is
    // the same link as base-target + stored pin and must not churn the row
    dbLink.setVersionPin(4L);
    ApiExtraField apiField = incomingLinkField(5L, "SA2v4", null);

    boolean changed =
        helper.applyExistingLinkFieldChanges(List.of(apiField), List.of(dbField), user);

    assertFalse(changed);
    verify(inventoryLinkManager, never()).updateLink(any(), any(), any());
  }

  @Test
  void emptyIncomingListIsANoop() {
    assertFalse(
        helper.applyExistingLinkFieldChanges(Collections.emptyList(), List.of(dbField), user));
  }
}
