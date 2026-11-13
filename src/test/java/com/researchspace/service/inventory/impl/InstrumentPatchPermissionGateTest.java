package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventorySharingMode;
import com.researchspace.booking.service.BookingCalendarRevocationCoordinator;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InstrumentPatchPermissionGateTest {
  @Test
  void contentOnlyDoesNotRegister() {
    assertGate(false, false);
  }

  @Test
  void emptyAclRegistersBeforeAnyRead() {
    assertGate(true, false);
  }

  @Test
  void sharingModeRegistersBeforeAnyRead() {
    assertGate(false, true);
  }

  private void assertGate(boolean acl, boolean mode) {
    InstrumentEntityApiManagerImpl manager = spy(new InstrumentEntityApiManagerImpl());
    BookingCalendarRevocationCoordinator revocations =
        mock(BookingCalendarRevocationCoordinator.class);
    ReflectionTestUtils.setField(manager, "calendarRevocations", revocations);
    InstrumentDao instruments = mock(InstrumentDao.class);
    ReflectionTestUtils.setField(manager, "instrumentDao", instruments);
    ApiInstrument patch = new ApiInstrument();
    patch.setId(5L);
    patch.setName("new name");
    if (acl) patch.setSharedWith(List.of());
    if (mode) patch.setSharingMode(ApiInventorySharingMode.OWNER_ONLY);
    User subject = new User();
    when(instruments.lockById(5L)).thenThrow(new IllegalStateException("first database access"));
    assertThrows(IllegalStateException.class, () -> manager.updateApiInstrument(patch, subject));
    if (acl || mode) {
      var order = inOrder(revocations, instruments);
      order.verify(revocations).affectInstrument(5L);
      order.verify(instruments).lockById(5L);
    } else {
      verifyNoInteractions(revocations);
    }
  }
}
