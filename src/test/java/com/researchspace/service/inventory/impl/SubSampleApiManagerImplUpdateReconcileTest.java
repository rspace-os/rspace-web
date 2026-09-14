package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.dao.SubSampleDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.ApiBarcodesHelper;
import com.researchspace.service.inventory.ApiExtraFieldsHelper;
import com.researchspace.service.inventory.ApiIdentifiersHelper;
import com.researchspace.service.inventory.InventoryMoveHelper;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SampleSiblingRowLock;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * The locking and reconciliation {@code updateApiSubSample} performs before it applies an edit.
 *
 * <p>Its only other guard is {@code SampleDynamicUpdateIT}, an {@code *IT}, which does not run on a
 * feature branch: the GitHub Action runs {@code mvnw clean test -Dfast=true} and the Jenkinsfile
 * runs the quick tests. Deleting the {@code reconcileWithCommittedRow} call left the fast tier
 * entirely green (review 2026-09-14, I1).
 */
@ExtendWith(MockitoExtension.class)
class SubSampleApiManagerImplUpdateReconcileTest {

  @Mock private SubSampleDao subSampleDao;
  @Mock private InventoryPermissionUtils invPermissions;
  @Mock private SampleApiManager sampleApiMgr;
  @Mock private SampleSiblingRowLock siblingRowLock;
  @Mock private InventoryMoveHelper moveHelper;
  @Mock private ApiExtraFieldsHelper extraFieldHelper;
  @Mock private ApiBarcodesHelper barcodesHelper;
  @Mock private ApiIdentifiersHelper identifiersHelper;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private InventoryEditLockTracker tracker;
  @Mock private UserManager userManager;
  @Mock private Subject subject;
  @InjectMocks private SubSampleApiManagerImpl subSampleApiMgr;

  private final User user = ownerNamed("someone");

  @BeforeEach
  void bindSubject() {
    ThreadContext.bind(subject);
  }

  @AfterEach
  void unbindSubject() {
    ThreadContext.unbindSubject();
  }

  private static User ownerNamed(String username) {
    User owner = new User(username);
    owner.setId(7L);
    return owner;
  }

  private SubSample cachedSubSampleHolding(String millilitres) {
    SubSample subSample = new SubSample();
    subSample.setId(100L);
    Sample parent = new Sample();
    parent.setId(20L);
    parent.setOwner(user);
    subSample.setSample(parent);
    subSample.setQuantity(millilitres(millilitres));
    return subSample;
  }

  /** The edit-lock tracker reports an already-held lock, so the call takes no temporary lock. */
  private void trackerGrantsTheLock() {
    ApiInventoryEditLock granted = new ApiInventoryEditLock();
    granted.setStatus(ApiInventoryEditLockStatus.WAS_ALREADY_LOCKED);
    when(tracker.attemptToLockForEdit(any(), any())).thenReturn(granted);
  }

  private static QuantityInfo millilitres(String value) {
    return new QuantityInfo(new BigDecimal(value), RSUnitDef.MILLI_LITRE.getId());
  }

  private void stubTheEditOf(SubSample cached, QuantityInfo committed) {
    // Read out of the entity BEFORE the stubbing below: reading a spy inside when(..) is a nested
    // call Mockito reports as UnfinishedStubbing.
    long committedVersion = cached.getVersion();
    when(subSampleDao.exists(100L)).thenReturn(true);
    when(subSampleDao.get(100L)).thenReturn(cached);
    trackerGrantsTheLock();
    when(subSampleDao.lockRowForUpdate(100L)).thenReturn(cached);
    when(subSampleDao.getQuantityForUpdate(100L)).thenReturn(committed);
    lenient().when(subSampleDao.getVersionForUpdate(100L)).thenReturn(committedVersion);
    lenient().when(subSampleDao.save(any(SubSample.class))).thenAnswer(i -> i.getArgument(0));
  }

  private static ApiSubSample edit(QuantityInfo quantity) {
    ApiSubSample incoming = new ApiSubSample();
    incoming.setId(100L);
    if (quantity != null) {
      incoming.setQuantity(new ApiQuantityInfo(quantity));
    }
    return incoming;
  }

  @Test
  void aQuantityEditTakesTheSiblingSetLockBeforeTheRowLock() {
    // Applying the edit runs SubSample.setQuantity, which cascades into
    // SampleEntity.recalculateTotalQuantity and sums the sibling ENTITIES from this transaction's
    // snapshot. A decrement committed against a sibling while this edit was in flight is not in
    // that snapshot, so the recomputed total is written from stale stock and the sample advertises
    // material that does not exist (review 2026-09-14, C1).
    //
    // The remedy is the sibling-set lock, and the ORDER is not negotiable: this subsample's own row
    // is one of its parent's sibling rows, so acquiring the set after the per-row lock is the
    // inversion registerApiSubSampleUsage documents, where two edits on two siblings each hold the
    // row the other wants. Sibling set first, then rows, is the order every stock writer here uses.
    SubSample cached = cachedSubSampleHolding("10");
    stubTheEditOf(cached, millilitres("10"));

    subSampleApiMgr.updateApiSubSample(edit(millilitres("4")), user);

    InOrder order = inOrder(siblingRowLock, subSampleDao);
    order.verify(siblingRowLock).lockSiblingRowsAndRecalculateTotal(20L);
    order.verify(subSampleDao).lockRowForUpdate(100L);
  }

  @Test
  void theParentTotalIsRecomputedFromTheLockedRowsAfterTheEditIsSaved() {
    // recalculateTotalQuantity has already written a total summed from unlocked sibling entities by
    // the time the save happens. The last word has to be the recompute from the rows read under
    // their locks, which is what lockSiblingRowsAndRecalculateTotal does, so it runs again after
    // the save rather than only before the edit.
    SubSample cached = cachedSubSampleHolding("10");
    stubTheEditOf(cached, millilitres("10"));

    subSampleApiMgr.updateApiSubSample(edit(millilitres("4")), user);

    InOrder order = inOrder(siblingRowLock, subSampleDao);
    order.verify(siblingRowLock).lockSiblingRowsAndRecalculateTotal(20L);
    order.verify(subSampleDao).save(any(SubSample.class));
    order.verify(siblingRowLock).lockSiblingRowsAndRecalculateTotal(20L);
  }

  @Test
  void anExplicitQuantityInThePayloadIsStillApplied() {
    // The reconcile replaces the entity's cached quantity with the committed one BEFORE the edit is
    // applied, so updateQuantity's equality check now compares the user's value against the
    // committed value rather than the loaded one. Inverting those two steps would make every
    // deliberate quantity correction a silent no-op, and nothing at any tier asserted otherwise
    // (review 2026-09-14, I4).
    SubSample cached = cachedSubSampleHolding("10");
    stubTheEditOf(cached, millilitres("6"));

    subSampleApiMgr.updateApiSubSample(edit(millilitres("4")), user);

    assertEquals(
        0,
        new BigDecimal("4").compareTo(cached.getQuantity().getNumericValue()),
        "a quantity the user explicitly sent must reach the entity");
  }

  @Test
  void readsTheCommittedQuantityBeforeAnythingDirtiesTheEntity() {
    // Every read in the reconcile is an HQL query against the SubSample table, and Hibernate's
    // default AUTO flush mode flushes pending changes to a query's tables before running it. So a
    // mutation that ran first would flush the whole row from the pre-lock snapshot, and the scalar
    // would then read back what this transaction just wrote instead of what the other transaction
    // committed: the reconcile becomes a no-op and the decrement it exists to preserve is reverted.
    //
    // Mocks have no flush, so the ordering is modelled the way
    // SubSampleApiManagerImplUsageVersionTest models it: the stubbed query returns the entity's own
    // value once the entity has been dirtied, which is what a flush would leave behind, and the
    // committed value only while it is still clean.
    SubSample cached = spy(cachedSubSampleHolding("10"));
    long committedVersion = cached.getVersion();
    AtomicBoolean dirtied = new AtomicBoolean(false);
    doAnswer(
            invocation -> {
              dirtied.set(true);
              return invocation.callRealMethod();
            })
        .when(cached)
        .setName(any());
    when(subSampleDao.exists(100L)).thenReturn(true);
    when(subSampleDao.get(100L)).thenReturn(cached);
    trackerGrantsTheLock();
    when(subSampleDao.lockRowForUpdate(100L)).thenReturn(cached);
    when(subSampleDao.getQuantityForUpdate(100L))
        .thenAnswer(invocation -> dirtied.get() ? cached.getQuantity() : millilitres("6"));
    lenient().when(subSampleDao.getVersionForUpdate(100L)).thenReturn(committedVersion);
    lenient().when(subSampleDao.save(any(SubSample.class))).thenAnswer(i -> i.getArgument(0));

    ApiSubSample rename = edit(null);
    rename.setName("renamed while the stock was being spent");
    subSampleApiMgr.updateApiSubSample(rename, user);

    assertEquals(
        0,
        new BigDecimal("6").compareTo(cached.getQuantity().getNumericValue()),
        "the entity must carry the committed quantity: finding 10 means the read was served from a"
            + " flush of this transaction's own snapshot");
  }

  @Test
  void everyLockedReadPrecedesTheFirstReconcilingMutation() {
    // Same AUTO-flush rule, stated as an ordering rather than through a stubbed flush, so the guard
    // survives someone reordering the three reads among themselves.
    // refreshParentLocationFromLockedRow is a query too, so it belongs on the read side of the
    // line, not with the assignments it sits next to.
    SubSample cached = spy(cachedSubSampleHolding("10"));
    stubTheEditOf(cached, millilitres("6"));

    subSampleApiMgr.updateApiSubSample(edit(millilitres("4")), user);

    InOrder order = inOrder(subSampleDao, cached);
    order.verify(subSampleDao).getQuantityForUpdate(100L);
    order.verify(subSampleDao).getVersionForUpdate(100L);
    order.verify(subSampleDao).refreshParentLocationFromLockedRow(cached);
    order.verify(cached).refreshQuantityFromLockedRow(any());
    order.verify(cached).refreshVersionFromLockedRow(any());
  }

  @Test
  void theEditIsAppliedOnlyAfterTheRowLockIsHeld() {
    // The reconcile is worth nothing if the row it read is not locked for the rest of the
    // transaction: another writer could commit between the read and the write. Deleting the
    // reconcile call outright left the whole fast tier green, which is why this is asserted at all
    // (review 2026-09-14, I1).
    SubSample cached = spy(cachedSubSampleHolding("10"));
    stubTheEditOf(cached, millilitres("6"));

    subSampleApiMgr.updateApiSubSample(edit(millilitres("4")), user);

    InOrder order = inOrder(subSampleDao, cached);
    order.verify(subSampleDao).lockRowForUpdate(100L);
    order.verify(subSampleDao).getQuantityForUpdate(100L);
    order.verify(cached).setQuantity(any());
  }
}
