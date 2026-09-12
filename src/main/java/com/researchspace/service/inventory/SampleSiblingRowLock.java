package com.researchspace.service.inventory;

/**
 * The canonical FIRST lock of any transaction that writes Inventory stock: an exclusive row lock on
 * every active subsample of one sample, taken in one place so that every stock writer acquires its
 * lock groups in the same order (sibling sets ascending by sample id, then individual rows) and no
 * two can deadlock against each other. See DevDocs/adr/0007.
 *
 * <p>Separate from {@link SampleApiManager} deliberately. This is a lock primitive with no {@code
 * User} and no permission assertion, unlike every mutator on that interface, and it sat there
 * looking like an ordinary public mutator: through {@code POST /listOfMaterials} a caller holding
 * only limited-read on one subsample could take exclusive row locks across a sample they cannot
 * edit, for the life of the request. Narrowing it to this one-method type does not change who may
 * call it, but it stops the lock reading as part of the sample API and names what it is (parallel
 * review, L5 and P4-S3).
 */
public interface SampleSiblingRowLock {

  /**
   * Locks every active subsample row of the sample, and, as a consequence of having read them,
   * rewrites the sample's denormalised total from those locked rows.
   *
   * <p>THE LOCK IS THE CONTRACT. Five call sites invoke this purely for the lock, before touching
   * anything, and the recompute is what falls out of reading the rows under it. An optimisation
   * that skipped the recompute - "nothing changed, so there is nothing to sum" - would be entirely
   * reasonable against the old name and silently remove the deadlock ordering three unrelated
   * features depend on.
   *
   * <p>Call it AFTER changing any subsample of the sample as well: {@code
   * SampleEntity.recalculateTotalQuantity}, which {@code SubSample.setQuantity} triggers as a
   * cascade, sums the sibling ENTITIES instead, and in InnoDB an unlocked sibling is read from the
   * transaction's snapshot, so two writers on different siblings each compute the total from stale
   * stock and one decrement is lost from it. Locking the siblings and refreshing them was not
   * enough either: a refreshed sibling was measured reverting to its pre-lock value later in the
   * same request. This reads the values as scalars, which the persistence context cannot serve or
   * re-stale, so it is the last word on the total (code review finding 2, reproduced by {@code
   * parallelAliquotsOnSiblingSubSamplesKeepTheParentTotalExact}; the lock itself is pinned by
   * {@code GenericDaoLockScopeIT} and {@code SubSampleDaoTest}).
   *
   * <p>PRECONDITION: a transaction is already open. The lock is held until that transaction ends,
   * so a caller that is not in one would be protected by nothing; the implementation declares
   * {@code Propagation.MANDATORY} so that call fails instead of silently locking nothing.
   *
   * @param sampleId the parent sample whose subsample rows are to be locked. Every caller derives
   *     it from a live subsample it has just read, so a sample that no longer exists means the
   *     parent row vanished mid-transaction: there is nothing to lock and nothing to sum, and the
   *     call returns having done neither.
   */
  void lockSiblingRowsAndRecalculateTotal(Long sampleId);
}
