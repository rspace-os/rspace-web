package com.researchspace.service.inventory.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.inventory.InventoryRecord;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pure unit tests for {@link InventoryApiManagerImpl#increaseVersionOncePerTransaction}, driving
 * {@link TransactionSynchronizationManager} directly the way Spring's transaction manager would
 * (RSDEV-1319). No Spring context or database.
 */
public class InventoryApiManagerImplVersionBumpTest {

  private static final String BUMPED_IN_TX =
      InventoryApiManagerImpl.class.getName() + ".versionBumped";

  @SuppressWarnings("unchecked")
  private final InventoryApiManagerImpl<InventoryRecord> manager =
      mock(InventoryApiManagerImpl.class, Mockito.CALLS_REAL_METHODS);

  @BeforeEach
  public void startTransaction() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
  }

  @AfterEach
  public void cleanUpThreadState() {
    TransactionSynchronizationManager.unbindResourceIfPossible(BUMPED_IN_TX);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  private InventoryRecord record(Long id, String globalId) {
    InventoryRecord record = mock(InventoryRecord.class);
    when(record.getId()).thenReturn(id);
    when(record.getGlobalIdentifier()).thenReturn(globalId);
    return record;
  }

  /** Simulates the transaction outcome the way AbstractPlatformTransactionManager would. */
  private void completeTransaction(int status) {
    for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
      s.afterCompletion(status);
    }
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  public void sameRecordBumpsOncePerTransaction() {
    InventoryRecord sample = record(5L, "SA5");

    manager.increaseVersionOncePerTransaction(sample);
    manager.increaseVersionOncePerTransaction(sample);

    verify(sample, times(1)).increaseVersion();
  }

  @Test
  public void distinctRecordsEachBump() {
    InventoryRecord sample = record(5L, "SA5");
    InventoryRecord otherSample = record(6L, "SA6");

    manager.increaseVersionOncePerTransaction(sample);
    manager.increaseVersionOncePerTransaction(otherSample);

    verify(sample).increaseVersion();
    verify(otherSample).increaseVersion();
  }

  @Test
  public void sameNumericIdDifferentTypeBothBump() {
    InventoryRecord sample = record(5L, "SA5");
    InventoryRecord subSample = record(5L, "SS5");

    manager.increaseVersionOncePerTransaction(sample);
    manager.increaseVersionOncePerTransaction(subSample);

    verify(sample).increaseVersion();
    verify(subSample).increaseVersion();
  }

  @Test
  public void transientRecordsWithNullIdAlwaysBump() {
    // an unsaved record has no id, so getGlobalIdentifier() is null and there is no revision to
    // deduplicate against; two distinct transient records must not suppress each other
    InventoryRecord transientA = record(null, null);
    InventoryRecord transientB = record(null, null);

    manager.increaseVersionOncePerTransaction(transientA);
    manager.increaseVersionOncePerTransaction(transientB);
    manager.increaseVersionOncePerTransaction(transientB);

    verify(transientA).increaseVersion();
    verify(transientB, times(2)).increaseVersion();
  }

  @Test
  public void outsideTransactionEveryCallBumps() {
    completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
    InventoryRecord sample = record(5L, "SA5");

    manager.increaseVersionOncePerTransaction(sample);
    manager.increaseVersionOncePerTransaction(sample);

    verify(sample, times(2)).increaseVersion();
  }

  @Test
  public void guardResetsAfterCommit() {
    InventoryRecord sample = record(5L, "SA5");
    manager.increaseVersionOncePerTransaction(sample);
    completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    manager.increaseVersionOncePerTransaction(sample);

    verify(sample, times(2)).increaseVersion();
  }

  @Test
  public void guardResetsAfterRollback() {
    // if afterCompletion only fired on commit, a rolled-back transaction would leave the set
    // bound to the pooled thread and suppress this record's bumps forever
    InventoryRecord sample = record(5L, "SA5");
    manager.increaseVersionOncePerTransaction(sample);
    completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    manager.increaseVersionOncePerTransaction(sample);

    verify(sample, times(2)).increaseVersion();
  }

  @Test
  public void suspendedTransactionDoesNotLeakGuardIntoInnerTransaction() {
    // REQUIRES_NEW: Spring suspends the outer transaction's synchronizations, runs the inner
    // transaction (its own Envers revision, so its own bump), then resumes the outer one
    InventoryRecord sample = record(5L, "SA5");
    manager.increaseVersionOncePerTransaction(sample);

    List<TransactionSynchronization> outerSyncs =
        TransactionSynchronizationManager.getSynchronizations();
    outerSyncs.forEach(TransactionSynchronization::suspend);
    TransactionSynchronizationManager.clearSynchronization();

    // inner transaction: must get its own set, and its own bump for the same record
    TransactionSynchronizationManager.initSynchronization();
    manager.increaseVersionOncePerTransaction(sample);
    verify(sample, times(2)).increaseVersion();
    completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

    // outer transaction resumes: its own set is restored, so the record stays suppressed
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    outerSyncs.forEach(TransactionSynchronization::resume);
    manager.increaseVersionOncePerTransaction(sample);
    verify(sample, times(2)).increaseVersion();
  }
}
