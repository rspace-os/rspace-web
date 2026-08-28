package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * New link extra-fields (newFieldRequest) are persisted via {@code buildExtraLinkField}, a path the
 * controller-layer validator cannot fully guard: the payload's parentGlobalId is client-supplied
 * and can be forged, so the self-link rule must hold here against the authoritative parent record
 * (valid-payload review, finding 1). Covers both the operations origin-field route and ordinary
 * PUT/POST field creation, which share this code.
 */
@ExtendWith(MockitoExtension.class)
class ApiExtraFieldsHelperLinkCreateTest {

  @Mock private InventoryLinkManager inventoryLinkManager;
  private ApiExtraFieldsHelper helper;
  private User user;
  private InventoryRecord parent;

  @BeforeEach
  void setUp() {
    helper = new ApiExtraFieldsHelper(new RecordFactory());
    ReflectionTestUtils.setField(helper, "inventoryLinkManager", inventoryLinkManager);
    user = TestFactory.createAnyUser("any");
    parent = mock(InventoryRecord.class);
  }

  private ApiExtraField newLinkFieldTargeting(String targetGlobalId) {
    ApiExtraField apiField = new ApiExtraField(ExtraFieldTypeEnum.LINK);
    apiField.setName("References " + targetGlobalId);
    apiField.setNewFieldRequest(true);
    ApiInventoryLink apiLink = new ApiInventoryLink();
    apiLink.setRelationType("References");
    apiLink.setTargetGlobalId(targetGlobalId);
    apiField.setLink(apiLink);
    return apiField;
  }

  @Test
  void newLinkFieldTargetingItsOwnParentIsRejected() {
    when(parent.getGlobalIdentifier()).thenReturn("SS100");
    ApiExtraField selfLink = newLinkFieldTargeting("SS100");

    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class,
            () -> helper.addExtraFieldsForNewInventoryRecord(List.of(selfLink), parent, user));

    assertEquals("errors.inventory.field.link.selfLinkForbidden", ex.getErrorCode());
    verify(inventoryLinkManager, never()).createLink(any(), any());
    verify(parent, never()).addExtraField(any());
  }

  @Test
  void newLinkFieldTargetingAnotherRecordIsPersisted() {
    when(parent.getGlobalIdentifier()).thenReturn("SS100");
    ApiExtraField link = newLinkFieldTargeting("SS200");

    helper.addExtraFieldsForNewInventoryRecord(List.of(link), parent, user);

    verify(inventoryLinkManager).createLink(link.getLink(), user);
    verify(parent).addExtraField(any());
  }

  @Test
  void parentWithoutAnIdentityYetCannotBeSelfLinked() {
    // a record still being created has no global id (getGlobalIdentifier() is null), so no target
    // can point back at it; the check must not throw on the null
    when(parent.getGlobalIdentifier()).thenReturn(null);
    ApiExtraField link = newLinkFieldTargeting("SS100");

    helper.addExtraFieldsForNewInventoryRecord(List.of(link), parent, user);

    verify(inventoryLinkManager).createLink(link.getLink(), user);
  }
}
