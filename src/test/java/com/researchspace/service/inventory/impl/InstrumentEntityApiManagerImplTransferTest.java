package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.UserGroupDao;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** Instrument transfers re-check authority under database locks and audit the real actor. */
@ExtendWith(MockitoExtension.class)
class InstrumentEntityApiManagerImplTransferTest {

  private static final long INSTRUMENT_ID = 5L;

  @Mock private InstrumentDao instrumentDao;
  @Mock private UserGroupDao userGroupDao;
  @Mock private InventoryPermissionUtils invPermissions;
  @Mock private InventoryEditLockTracker tracker;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private UserManager userManager;

  private InstrumentEntityApiManagerImpl manager;
  private User owner;
  private User pi;
  private User newOwner;
  private Instrument instrument;

  @BeforeEach
  void setUp() {
    manager = new InstrumentEntityApiManagerImpl();
    ReflectionTestUtils.setField(manager, "instrumentDao", instrumentDao);
    ReflectionTestUtils.setField(manager, "userGroupDao", userGroupDao);
    ReflectionTestUtils.setField(manager, "invPermissions", invPermissions);
    ReflectionTestUtils.setField(manager, "tracker", tracker);
    ReflectionTestUtils.setField(manager, "publisher", publisher);
    ReflectionTestUtils.setField(manager, "userManager", userManager);
    owner = TestFactory.createAnyUser("owner");
    pi = TestFactory.createAnyUser("pi");
    newOwner = TestFactory.createAnyUser("newOwner");
    owner.setId(1L);
    pi.setId(2L);
    newOwner.setId(3L);
    instrument = instrumentOwnedBy(owner);

    when(instrumentDao.exists(INSTRUMENT_ID)).thenReturn(true);
    when(instrumentDao.get(INSTRUMENT_ID)).thenReturn(instrument);
    ApiInventoryEditLock lock = new ApiInventoryEditLock();
    lock.setStatus(ApiInventoryEditLockStatus.LOCKED_OK);
    when(tracker.attemptToLockForEdit(any(), eq(pi))).thenReturn(lock);
  }

  private Instrument instrumentOwnedBy(User instrumentOwner) {
    Instrument instrument = new Instrument();
    instrument.setId(INSTRUMENT_ID);
    instrument.setName("transfer me");
    instrument.setOwner(instrumentOwner);
    return instrument;
  }

  private ApiInstrument transferRequest() {
    ApiInstrument request = new ApiInstrument();
    request.setId(INSTRUMENT_ID);
    request.setOwner(new ApiUser(newOwner));
    return request;
  }

  @Test
  void transferIsRefusedWhenTheLockedReReadShowsNoGroupAuthority() {
    // the unlocked pre-check passes, then the PI is demoted or the owner leaves the group
    doNothing().when(invPermissions).assertUserCanTransferInventoryRecord(any(), eq(pi));
    when(userGroupDao.hasLockedTransferAuthority(pi, owner)).thenReturn(false);
    doCallRealMethod()
        .when(invPermissions)
        .assertUserCanTransferInventoryRecord(instrument, pi, false);
    when(invPermissions.canUserReadInventoryRecord(instrument, pi)).thenReturn(true);

    IllegalArgumentException refused =
        assertThrows(
            IllegalArgumentException.class,
            () -> manager.changeApiInstrumentOwner(transferRequest(), pi));

    assertEquals(
        "INSTRUMENT with id [5] cannot be transferred by current User", refused.getMessage());
    InOrder lockOrder = inOrder(instrumentDao, userGroupDao);
    lockOrder.verify(instrumentDao).refreshForUpdate(instrument);
    lockOrder.verify(userGroupDao).hasLockedTransferAuthority(pi, owner);
    verify(instrumentDao, never()).save(any());
    verify(publisher, never()).publishEvent(any());
    verify(tracker).attemptToUnlock(instrument.getGlobalIdentifier(), pi);
  }

  @Test
  void operateAsTransferRecordsTheOriginalUserAsActor() {
    User sysadmin = TestFactory.createAnyUser("sysadmin");
    when(userGroupDao.hasLockedTransferAuthority(pi, owner)).thenReturn(true);
    when(userManager.userExists(newOwner.getUsername())).thenReturn(true);
    when(userManager.getUserByUsername(newOwner.getUsername())).thenReturn(newOwner);

    manager.changeApiInstrumentOwner(transferRequest(), pi, sysadmin);

    verify(invPermissions).assertUserCanTransferInventoryRecord(instrument, pi, true);
    verify(instrumentDao).save(instrument);
    ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
    verify(publisher, atLeastOnce()).publishEvent(events.capture());
    InventoryTransferEvent transfer =
        events.getAllValues().stream()
            .filter(InventoryTransferEvent.class::isInstance)
            .map(InventoryTransferEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertSame(sysadmin, transfer.getTransferringUser());
    assertSame(owner, transfer.getOriginalOwner());
    assertSame(newOwner, transfer.getNewOwner());
  }
}
