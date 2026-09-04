package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.SubSampleDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The locked read behind every operation origin. It is the only thing between a raw origin id in a
 * request body and a mutation of that subsample, so its two guarantees are pinned here rather than
 * only through the callers that mock it.
 */
@ExtendWith(MockitoExtension.class)
class SubSampleApiManagerImplLockTest {

  @Mock private SubSampleDao subSampleDao;
  @Mock private InventoryPermissionUtils invPermissions;
  @Mock private MessageSourceUtils messages;
  @InjectMocks private SubSampleApiManagerImpl subSampleApiMgr;

  private final User user = new User("someone");

  @Test
  void unknownIdIsNotFoundWithLocalisedText() {
    when(subSampleDao.getForUpdate(404L)).thenReturn(null);
    when(messages.getMessage("errors.inventory.subsample.notFound", new Object[] {404L}))
        .thenReturn("No subsample with id: 404");

    // Reaches the user verbatim (ApiControllerAdvice copies getLocalizedMessage into the response
    // body), so it must come from the catalog, not a hard-coded English literal.
    assertEquals(
        "No subsample with id: 404",
        assertThrows(
                NotFoundException.class, () -> subSampleApiMgr.lockSubSampleForEdit(404L, user))
            .getMessage());
    verify(invPermissions, never())
        .assertUserCanEditInventoryRecord(nullable(InventoryRecord.class), any());
  }

  @Test
  void locksTheRowThenAssertsEditPermission() {
    SubSample locked = new SubSample();
    when(subSampleDao.getForUpdate(100L)).thenReturn(locked);

    assertEquals(locked, subSampleApiMgr.lockSubSampleForEdit(100L, user));

    // The lock must be held before the permission verdict, so a caller that passes cannot then be
    // overtaken by a concurrent edit between the check and the decrement.
    InOrder inOrder = inOrder(subSampleDao, invPermissions);
    inOrder.verify(subSampleDao).getForUpdate(100L);
    inOrder.verify(invPermissions).assertUserCanEditInventoryRecord(locked, user);
  }
}
