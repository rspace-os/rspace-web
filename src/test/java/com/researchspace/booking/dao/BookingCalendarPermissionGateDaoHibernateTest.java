package com.researchspace.booking.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class BookingCalendarPermissionGateDaoHibernateTest {

  private final SessionFactory sessions = mock(SessionFactory.class);
  private final Session session = mock(Session.class);
  private final Transaction transaction = mock(Transaction.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement statement = mock(PreparedStatement.class);
  private final ResultSet rows = mock(ResultSet.class);
  private final BookingCalendarPermissionGateDaoHibernate gate =
      new BookingCalendarPermissionGateDaoHibernate(sessions);

  @BeforeEach
  void begin() throws Exception {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    when(sessions.getCurrentSession()).thenReturn(session);
    when(session.getTransaction()).thenReturn(transaction);
    when(transaction.isActive()).thenReturn(true);
    when(connection.prepareStatement(any())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.next()).thenReturn(true, false);
    when(rows.getLong(1)).thenReturn(1L);
    doAnswer(
            invocation -> {
              invocation.<Work>getArgument(0).execute(connection);
              return null;
            })
        .when(session)
        .doWork(any());
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
  void directJdbcAcquisitionDoesNotAutoflush() {
    gate.lockShared();

    assertEquals(BookingCalendarPermissionGateDao.Mode.SHARED, gate.currentMode());
    verify(session, never()).flush();
  }

  @Test
  void inPlaceUpgradeMarksTheSourceTransactionRollbackOnly() {
    gate.lockShared();

    assertThrows(IllegalStateException.class, gate::lockExclusive);

    verify(transaction).markRollbackOnly();
  }

  @Test
  void suspendedAncestorIsIdentityOwnedAndRejectedByInnerWork() {
    gate.lockExclusive();
    List<TransactionSynchronization> synchronizations =
        TransactionSynchronizationManager.getSynchronizations();

    synchronizations.forEach(TransactionSynchronization::suspend);
    assertEquals(BookingCalendarPermissionGateDao.Mode.NONE, gate.currentMode());
    assertThrows(IllegalStateException.class, gate::lockShared);
    verify(session, times(1)).doWork(any());
    synchronizations.forEach(TransactionSynchronization::resume);

    assertEquals(BookingCalendarPermissionGateDao.Mode.EXCLUSIVE, gate.currentMode());
    synchronizations.forEach(
        synchronization ->
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    gate.assertNoSuspendedAncestor();
  }
}
