package com.researchspace.service.inventory.impl;

import com.researchspace.model.inventory.InventoryRecord;
import java.util.HashSet;
import java.util.Set;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Bumps an inventory record's user-facing version, at most once per record per transaction.
 *
 * <p>Why: Envers writes one revision per entity per transaction and stores only the final state, so
 * a second bump in the same transaction would advance the version past any revision carrying it,
 * leaving that version unresolvable (RSDEV-1319). The worked example lives in
 * DevDocs/DeveloperNotes/Transactions.md, "Envers revisions and inventory version bumps".
 *
 * <p>How: bumped records are remembered in a {@link Set} bound to the current transaction via
 * {@link TransactionSynchronizationManager}, keyed by global identifier (e.g. "SA5" vs "SS5") so
 * records of different types sharing a numeric id cannot collide. The set is shared by every
 * manager: a transaction that edits the same record through two different code paths still bumps it
 * once.
 *
 * <p>Lifecycle, each rule pinned by {@link TransactionScopedVersionBumpGuardTest}:
 *
 * <ul>
 *   <li>unbound in {@code afterCompletion}, commit and rollback alike, or a pooled request thread
 *       would keep suppressing bumps forever
 *   <li>unbound in {@code suspend()} and rebound in {@code resume()}, so a nested {@code
 *       REQUIRES_NEW} transaction gets its own set and its own bump
 *   <li>outside a real transaction every save commits on its own and gets its own revision, so the
 *       bump always goes ahead
 *   <li>an unsaved record (null id) has no revision to deduplicate against, so it always bumps
 * </ul>
 */
final class TransactionScopedVersionBumpGuard {

  private static final String BUMPED_IN_TX =
      TransactionScopedVersionBumpGuard.class.getName() + ".versionBumped";

  private TransactionScopedVersionBumpGuard() {}

  static void increaseVersionOncePerTransaction(InventoryRecord dbRecord) {
    if (firstVersionBumpInTransaction(dbRecord)) {
      dbRecord.increaseVersion();
    }
  }

  private static boolean firstVersionBumpInTransaction(InventoryRecord dbRecord) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()
        || dbRecord.getId() == null) {
      return true;
    }
    @SuppressWarnings("unchecked")
    Set<String> bumped = (Set<String>) TransactionSynchronizationManager.getResource(BUMPED_IN_TX);
    if (bumped == null) {
      Set<String> newSet = new HashSet<>();
      // register before binding: if registration fails nothing is left bound
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void suspend() {
              TransactionSynchronizationManager.unbindResourceIfPossible(BUMPED_IN_TX);
            }

            @Override
            public void resume() {
              TransactionSynchronizationManager.bindResource(BUMPED_IN_TX, newSet);
            }

            @Override
            public void afterCompletion(int status) {
              TransactionSynchronizationManager.unbindResourceIfPossible(BUMPED_IN_TX);
            }
          });
      bumped = newSet;
      TransactionSynchronizationManager.bindResource(BUMPED_IN_TX, bumped);
    }
    return bumped.add(dbRecord.getGlobalIdentifier());
  }
}
