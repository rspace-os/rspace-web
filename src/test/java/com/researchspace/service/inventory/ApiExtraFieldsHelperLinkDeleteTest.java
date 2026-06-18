package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.ExtraLinkField;
import com.researchspace.model.inventory.field.InventoryLink;
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
 * Deleting a Link extra-field must also soft-delete its backing {@link InventoryLink} through the
 * service-layer {@link InventoryLinkManager}. A field soft-delete only flips the field's {@code
 * deleted} flag, which neither dereferences the link nor cascades to it, so without this the link
 * row lingered with {@code deleted=false} (orphaned) while its parent field was gone.
 */
@ExtendWith(MockitoExtension.class)
class ApiExtraFieldsHelperLinkDeleteTest {

  @Mock private InventoryLinkManager inventoryLinkManager;
  private ApiExtraFieldsHelper helper;
  private User user;
  private InventoryRecord parent;

  @BeforeEach
  void setUp() {
    helper = new ApiExtraFieldsHelper(new RecordFactory());
    // the manager is field-autowired in production; wire the mock in directly
    ReflectionTestUtils.setField(helper, "inventoryLinkManager", inventoryLinkManager);
    user = TestFactory.createAnyUser("any");
    parent = mock(InventoryRecord.class);
  }

  private ApiExtraField deleteRequest(Long id) {
    ApiExtraField apiField = new ApiExtraField(ExtraFieldTypeEnum.LINK);
    apiField.setId(id);
    apiField.setDeleteFieldRequest(true);
    return apiField;
  }

  @Test
  void deletingALinkExtraFieldAlsoSoftDeletesItsLink() {
    InventoryLink dbLink = new InventoryLink();
    dbLink.setRelationType("References");
    dbLink.setTargetGlobalId("SA2");
    dbLink.setTargetPrefix(GlobalIdPrefix.SA);
    dbLink.setTargetDbId(2L);
    ExtraLinkField dbField = new ExtraLinkField();
    dbField.setId(5L);
    dbField.setLink(dbLink);

    boolean changed =
        helper.createDeleteRequestedExtraFields(
            List.of(deleteRequest(5L)), List.of(dbField), parent, user);

    assertTrue(changed);
    assertTrue(dbField.isDeleted());
    verify(inventoryLinkManager).deleteLink(dbLink, user);
  }

  @Test
  void deletingALinkFieldWithNoLinkYetDoesNotCallDeleteLink() {
    ExtraLinkField dbField = new ExtraLinkField();
    dbField.setId(5L);
    dbField.setLink(null);

    helper.createDeleteRequestedExtraFields(
        List.of(deleteRequest(5L)), List.of(dbField), parent, user);

    assertTrue(dbField.isDeleted());
    verify(inventoryLinkManager, never()).deleteLink(any(), any());
  }

  @Test
  void deletingANonLinkExtraFieldDoesNotCallDeleteLink() {
    ExtraField dbField = new RecordFactory().createExtraField(FieldType.TEXT);
    dbField.setId(7L);

    helper.createDeleteRequestedExtraFields(
        List.of(deleteRequest(7L)), List.of(dbField), parent, user);

    assertTrue(dbField.isDeleted());
    verify(inventoryLinkManager, never()).deleteLink(any(), any());
  }
}
