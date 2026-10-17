package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingCalendarCurrentAccessDao;
import com.researchspace.booking.dao.BookingCalendarPermissionGateDao;
import java.util.Set;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class BookingCalendarRevocationCoordinatorTest {

  private final BookingCalendarPermissionGateDao gate =
      mock(BookingCalendarPermissionGateDao.class);
  private final BookingCalendarCurrentAccessDao currentAccess =
      mock(BookingCalendarCurrentAccessDao.class);
  private final SessionFactory sessions = mock(SessionFactory.class);
  private final Session session = mock(Session.class);
  private final BookingCalendarRevocationCoordinator coordinator =
      new BookingCalendarRevocationCoordinator(gate, currentAccess, sessions);

  @BeforeEach
  void begin() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    when(sessions.getCurrentSession()).thenReturn(session);
  }

  @AfterEach
  void cleanup() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      for (TransactionSynchronization synchronization :
          TransactionSynchronizationManager.getSynchronizations()) {
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
      }
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void coalescesTheFinalScopeAndFlushesAroundRevocation() {
    coordinator.affectUser(7L);
    coordinator.affectUser(7L);
    coordinator.affectInstrument(11L);

    synchronization().beforeCommit(false);

    verify(gate).lockExclusive();
    verify(currentAccess).revokeDenied(Set.of(7L), Set.of(11L), false);
    verify(session, org.mockito.Mockito.times(2)).flush();
  }

  @Test
  void aCaughtFinalizationFailureStillMarksTheSourceTransactionRollbackOnly() {
    coordinator.affectAll();
    RuntimeException failure = new RuntimeException("current read failed");
    when(currentAccess.revokeDenied(Set.of(), Set.of(), true)).thenThrow(failure);

    assertThrows(RuntimeException.class, () -> synchronization().beforeCommit(false));

    verify(gate).markRollbackOnly();
  }

  @Test
  void followOnPermissionMutationAfterFinalizationRejectsTheWholeTransaction() {
    coordinator.affectUser(7L);
    synchronization().beforeCommit(false);

    assertThrows(IllegalStateException.class, () -> coordinator.affectInstrument(11L));

    verify(gate).markRollbackOnly();
  }

  private TransactionSynchronization synchronization() {
    return TransactionSynchronizationManager.getSynchronizations().get(0);
  }
}
