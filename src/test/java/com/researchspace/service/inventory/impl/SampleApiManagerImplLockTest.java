package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.SampleDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
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
 * The locked read of an origin's parent sample. Decrementing a subsample rewrites the parent's
 * denormalised total, so an operation holds this lock as well as the origin's; the two guarantees
 * are pinned here rather than only through the operation manager that mocks it.
 */
@ExtendWith(MockitoExtension.class)
class SampleApiManagerImplLockTest {

  @Mock private SampleDao sampleDao;
  @Mock private InventoryPermissionUtils invPermissions;
  @Mock private MessageSourceUtils messages;
  @InjectMocks private SampleApiManagerImpl sampleApiMgr;

  private final User user = new User("someone");

  @Test
  void unknownIdIsNotFoundWithLocalisedText() {
    when(sampleDao.getForUpdate(404L)).thenReturn(null);
    when(messages.getMessage("errors.inventory.sample.notFound", new Object[] {404L}))
        .thenReturn("No sample with id: 404");

    assertEquals(
        "No sample with id: 404",
        assertThrows(NotFoundException.class, () -> sampleApiMgr.lockSampleForEdit(404L, user))
            .getMessage());
    verify(invPermissions, never())
        .assertUserCanEditInventoryRecord(nullable(InventoryRecord.class), any());
  }

  @Test
  void locksTheRowThenAssertsEditPermission() {
    Sample locked = new Sample();
    when(sampleDao.getForUpdate(100L)).thenReturn(locked);

    assertEquals(locked, sampleApiMgr.lockSampleForEdit(100L, user));

    // The lock must be held before the permission verdict, so a caller that passes cannot then be
    // overtaken by a concurrent edit between the check and the total's recalculation.
    InOrder inOrder = inOrder(sampleDao, invPermissions);
    inOrder.verify(sampleDao).getForUpdate(100L);
    inOrder.verify(invPermissions).assertUserCanEditInventoryRecord(locked, user);
  }
}
