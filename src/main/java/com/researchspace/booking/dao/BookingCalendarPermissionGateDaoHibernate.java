package com.researchspace.booking.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Direct-JDBC implementation that acquires the gate without triggering Hibernate autoflush. */
@Repository
public class BookingCalendarPermissionGateDaoHibernate implements BookingCalendarPermissionGateDao {

  private static final Logger LOG =
      LoggerFactory.getLogger(BookingCalendarPermissionGateDaoHibernate.class);
  private static final long GATE_ID = 1L;
  private static final Object RESOURCE_KEY = BookingCalendarPermissionGateDaoHibernate.class;
  private static final ThreadLocal<Deque<State>> SUSPENDED =
      ThreadLocal.withInitial(ArrayDeque::new);

  private final SessionFactory sessionFactory;

  public BookingCalendarPermissionGateDaoHibernate(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public void lockShared() {
    lock(Mode.SHARED);
  }

  @Override
  public void lockExclusive() {
    lock(Mode.EXCLUSIVE);
  }

  private void lock(Mode requested) {
    assertNoSuspendedAncestor();
    requireTransaction();
    State existing = state();
    if (existing != null) {
      if (existing.mode == Mode.EXCLUSIVE || existing.mode == requested) {
        return;
      }
      fail("Booking calendar permission gate cannot be upgraded in-place");
    }
    State acquired = new State(requested);
    try {
      session()
          .doWork(
              connection -> {
                String suffix = requested == Mode.EXCLUSIVE ? " for update" : " lock in share mode";
                try (PreparedStatement statement =
                    connection.prepareStatement(
                        "select id from BookingCalendarPermissionGate where id = ?" + suffix)) {
                  statement.setLong(1, GATE_ID);
                  try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next() || rows.getLong(1) != GATE_ID || rows.next()) {
                      throw new SQLException("Booking calendar permission gate row is missing");
                    }
                  }
                }
              });
      TransactionSynchronizationManager.bindResource(RESOURCE_KEY, acquired);
      TransactionSynchronizationManager.registerSynchronization(new GateSynchronization(acquired));
    } catch (RuntimeException failure) {
      markRollbackOnly();
      throw failure;
    }
  }

  @Override
  public Mode currentMode() {
    State state = state();
    return state == null ? Mode.NONE : state.mode;
  }

  @Override
  public void requireHeld(Mode required) {
    Mode actual = currentMode();
    boolean satisfied =
        actual == Mode.EXCLUSIVE || (actual == Mode.SHARED && required == Mode.SHARED);
    if (!satisfied) {
      fail("Booking calendar permission gate " + required + " is required");
    }
  }

  @Override
  public boolean hasActiveGate() {
    return state() != null;
  }

  @Override
  public void assertNoSuspendedAncestor() {
    if (!SUSPENDED.get().isEmpty()) {
      fail("Booking calendar REQUIRES_NEW work cannot run beneath an active permission gate");
    }
  }

  @Override
  public void markRollbackOnly() {
    try {
      Session session = sessionFactory.getCurrentSession();
      if (session.getTransaction().isActive()) {
        session.getTransaction().markRollbackOnly();
      }
    } catch (RuntimeException failure) {
      // Preserve the original failure when the transaction/session itself is already broken.
      LOG.warn("Could not mark the failed booking calendar transaction rollback-only", failure);
    }
  }

  private void fail(String message) {
    markRollbackOnly();
    throw new IllegalStateException(message);
  }

  private Session session() {
    return sessionFactory.getCurrentSession();
  }

  private static State state() {
    Object resource = TransactionSynchronizationManager.getResource(RESOURCE_KEY);
    return resource instanceof State value ? value : null;
  }

  private static void requireTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException(
          "Booking calendar permission gate requires an active synchronized transaction");
    }
  }

  private static final class State {
    private final Mode mode;

    private State(Mode mode) {
      this.mode = mode;
    }
  }

  private static final class GateSynchronization implements TransactionSynchronization {
    private final State owner;

    private GateSynchronization(State owner) {
      this.owner = owner;
    }

    @Override
    public void suspend() {
      if (TransactionSynchronizationManager.getResource(RESOURCE_KEY) == owner) {
        TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
        SUSPENDED.get().push(owner);
      }
    }

    @Override
    public void resume() {
      Deque<State> ancestors = SUSPENDED.get();
      if (ancestors.isEmpty() || ancestors.peek() != owner) {
        throw new IllegalStateException("Booking calendar permission gate resume order was broken");
      }
      ancestors.pop();
      if (ancestors.isEmpty()) {
        SUSPENDED.remove();
      }
      TransactionSynchronizationManager.bindResource(RESOURCE_KEY, owner);
    }

    @Override
    public void afterCompletion(int status) {
      if (TransactionSynchronizationManager.getResource(RESOURCE_KEY) == owner) {
        TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
      }
      Deque<State> ancestors = SUSPENDED.get();
      ancestors.removeFirstOccurrence(owner);
      if (ancestors.isEmpty()) {
        SUSPENDED.remove();
      }
    }
  }
}
