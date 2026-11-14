package com.researchspace.booking.dao;

/**
 * Transaction-scoped database gate for booking-calendar token creation and permission changes.
 *
 * <p>Callers must acquire this gate before taking any booking configuration, target, permission
 * source, or legacy booking fence lock.
 */
public interface BookingCalendarPermissionGateDao {

  enum Mode {
    NONE,
    SHARED,
    EXCLUSIVE
  }

  /** Acquires the shared side used by booking and calendar-link mutations. */
  void lockShared();

  /** Acquires the exclusive side used by permission-source mutations. */
  void lockExclusive();

  /** Returns the gate held by the current physical transaction. */
  Mode currentMode();

  /** Requires at least the requested mode in the current physical transaction. */
  void requireHeld(Mode required);

  /** True while the current thread is executing inside a transaction that owns this gate. */
  boolean hasActiveGate();

  /** Rejects a REQUIRES_NEW boundary nested beneath a suspended gate-owning transaction. */
  void assertNoSuspendedAncestor();

  /** Marks the current physical transaction rollback-only. */
  void markRollbackOnly();
}
