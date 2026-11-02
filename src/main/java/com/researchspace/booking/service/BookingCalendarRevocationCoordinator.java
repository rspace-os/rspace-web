package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingCalendarCurrentAccessDao;
import com.researchspace.booking.dao.BookingCalendarPermissionGateDao;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.orm.hibernate5.HibernateExceptionTranslator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Coalesces permission-source mutations and durably revokes unreadable item-calendar tokens before
 * their source transaction can commit.
 */
@Component
public class BookingCalendarRevocationCoordinator {

  private static final Logger LOG =
      LoggerFactory.getLogger(BookingCalendarRevocationCoordinator.class);
  private static final Object RESOURCE_KEY = BookingCalendarRevocationCoordinator.class;
  private static final HibernateExceptionTranslator EXCEPTIONS = new HibernateExceptionTranslator();

  private final BookingCalendarPermissionGateDao permissionGate;
  private final BookingCalendarCurrentAccessDao currentAccess;
  private final SessionFactory sessionFactory;

  public BookingCalendarRevocationCoordinator(
      BookingCalendarPermissionGateDao permissionGate,
      BookingCalendarCurrentAccessDao currentAccess,
      SessionFactory sessionFactory) {
    this.permissionGate = permissionGate;
    this.currentAccess = currentAccess;
    this.sessionFactory = sessionFactory;
  }

  /** Marks one user's current calendar subscriptions for revalidation. */
  public void affectUser(Long userId) {
    if (userId == null) {
      return;
    }
    stateForMutation().userIds.add(userId);
  }

  /** Marks one instrument's current calendar subscriptions for revalidation. */
  public void affectInstrument(Long instrumentId) {
    if (instrumentId == null) {
      return;
    }
    stateForMutation().instrumentIds.add(instrumentId);
  }

  /** Revalidates every item subscription after a shared membership/community permission change. */
  public void affectAll() {
    stateForMutation().all = true;
  }

  /** True when this physical transaction has registered a permission-source mutation. */
  public boolean hasPermissionChanges() {
    return state() != null;
  }

  private State stateForMutation() {
    requireTransaction();
    State state = state();
    if (state == null) {
      permissionGate.lockExclusive();
      state = new State();
      TransactionSynchronizationManager.bindResource(RESOURCE_KEY, state);
      TransactionSynchronizationManager.registerSynchronization(
          new CoordinatorSynchronization(state));
    }
    if (state.phase != Phase.COLLECTING) {
      fail("Permission-source mutation registered after calendar revocation finalization began");
    }
    return state;
  }

  private void finalizeRevocation(State owner) {
    if (state() != owner) {
      fail("Calendar revocation coordinator lost its transaction state");
    }
    owner.phase = Phase.FINALIZING;
    try {
      sessionFactory.getCurrentSession().flush();
      currentAccess.revokeDenied(owner.userIds, owner.instrumentIds, owner.all);
      sessionFactory.getCurrentSession().flush();
      owner.phase = Phase.CLOSED;
    } catch (RuntimeException failure) {
      permissionGate.markRollbackOnly();
      LOG.warn("Booking calendar revocation finalization failed", failure);
      // flush() bypasses the DAO translation proxy; JPA lock exceptions need translating too.
      RuntimeException translated = EXCEPTIONS.translateExceptionIfPossible(failure);
      if (translated != null) {
        throw translated;
      }
      throw failure;
    }
  }

  private void fail(String message) {
    permissionGate.markRollbackOnly();
    throw new IllegalStateException(message);
  }

  private static void requireTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException(
          "Calendar permission-source mutation requires an active synchronized transaction");
    }
  }

  private static State state() {
    Object resource = TransactionSynchronizationManager.getResource(RESOURCE_KEY);
    return resource instanceof State value ? value : null;
  }

  private enum Phase {
    COLLECTING,
    FINALIZING,
    CLOSED
  }

  private static final class State {
    private final Set<Long> userIds = new HashSet<>();
    private final Set<Long> instrumentIds = new HashSet<>();
    private boolean all;
    private Phase phase = Phase.COLLECTING;
  }

  private final class CoordinatorSynchronization implements TransactionSynchronization {
    private final State owner;

    private CoordinatorSynchronization(State owner) {
      this.owner = owner;
    }

    @Override
    public void beforeCommit(boolean readOnly) {
      finalizeRevocation(owner);
    }

    // Clear completed booking state before after-completion work opens a new transaction.
    @Override
    public int getOrder() {
      return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public void suspend() {
      if (TransactionSynchronizationManager.getResource(RESOURCE_KEY) == owner) {
        TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
      }
    }

    @Override
    public void resume() {
      if (TransactionSynchronizationManager.hasResource(RESOURCE_KEY)) {
        fail("Calendar revocation coordinator resume would overwrite transaction state");
      }
      TransactionSynchronizationManager.bindResource(RESOURCE_KEY, owner);
    }

    @Override
    public void afterCompletion(int status) {
      if (TransactionSynchronizationManager.getResource(RESOURCE_KEY) == owner) {
        TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
      }
      owner.phase = Phase.CLOSED;
    }
  }
}
