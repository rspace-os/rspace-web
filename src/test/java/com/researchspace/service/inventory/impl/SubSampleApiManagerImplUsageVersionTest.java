package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.dao.SubSampleDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.SampleApiManager;
import java.math.BigDecimal;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * The user-facing version a stock decrement advances. Two operations on one subsample can both load
 * the entity at version N before either takes the row lock; the second must not reuse the number
 * the first already committed, or the intermediate stock state stops being addressable by its
 * version (Codex review, PR #1090).
 */
@ExtendWith(MockitoExtension.class)
class SubSampleApiManagerImplUsageVersionTest {

  @Mock private SubSampleDao subSampleDao;
  @Mock private InventoryPermissionUtils invPermissions;
  @Mock private SampleApiManager sampleApiMgr;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private InventoryEditLockTracker tracker;
  @Mock private UserManager userManager;
  @Mock private Subject subject;
  @InjectMocks private SubSampleApiManagerImpl subSampleApiMgr;

  private final User user = ownerNamed("someone");

  @BeforeEach
  void bindSubject() {
    // setModifiedBy resolves the operating-as user through Shiro; bind a thread-local mock rather
    // than mutating the global SecurityManager.
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

  private SubSample subSampleAtVersion(long version) {
    SubSample subSample = new SubSample();
    subSample.setId(100L);
    Sample parent = new Sample();
    parent.setId(20L);
    parent.setOwner(user);
    subSample.setSample(parent);
    subSample.setQuantity(millilitres("10"));
    for (long v = subSample.getVersion(); v < version; v++) {
      subSample.increaseVersion();
    }
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

  @Test
  void versionAdvancesFromTheCommittedRowNotTheStaleCachedEntity() {
    // Both requests loaded the entity at version 3. The other one committed 5 while this one waited
    // on the row lock, and lockRowForUpdate hands back the same cached instance, so the entity
    // still
    // says 3. Bumping that would write 4 again over the committed 5.
    SubSample cached = subSampleAtVersion(3L);
    when(subSampleDao.exists(100L)).thenReturn(true);
    when(subSampleDao.get(100L)).thenReturn(cached);
    trackerGrantsTheLock();
    when(subSampleDao.lockRowForUpdate(100L)).thenReturn(cached);
    when(subSampleDao.getQuantityForUpdate(100L)).thenReturn(millilitres("6"));
    when(subSampleDao.getVersionForUpdate(100L)).thenReturn(5L);
    when(subSampleDao.save(any(SubSample.class))).thenAnswer(i -> i.getArgument(0));

    subSampleApiMgr.registerApiSubSampleUsage(100L, millilitres("1"), user);

    assertEquals(6L, cached.getVersion());
  }

  @Test
  void versionStillAdvancesWhenTheRowMatchesTheCachedEntity() {
    // The uncontended case: nothing committed while we waited, so the committed version equals the
    // cached one and the bump is the ordinary +1.
    SubSample cached = subSampleAtVersion(3L);
    when(subSampleDao.exists(100L)).thenReturn(true);
    when(subSampleDao.get(100L)).thenReturn(cached);
    trackerGrantsTheLock();
    when(subSampleDao.lockRowForUpdate(100L)).thenReturn(cached);
    when(subSampleDao.getQuantityForUpdate(100L)).thenReturn(millilitres("10"));
    when(subSampleDao.getVersionForUpdate(100L)).thenReturn(3L);
    when(subSampleDao.save(any(SubSample.class))).thenAnswer(i -> i.getArgument(0));

    subSampleApiMgr.registerApiSubSampleUsage(100L, millilitres("1"), user);

    assertEquals(4L, cached.getVersion());
  }

  @Test
  void aUsageThatChangesNothingLeavesTheVersionAlone() {
    // Zero usage is a no-op read, so it must not consume a version even though the row was locked.
    SubSample cached = subSampleAtVersion(3L);
    when(subSampleDao.exists(100L)).thenReturn(true);
    when(subSampleDao.get(100L)).thenReturn(cached);

    subSampleApiMgr.registerApiSubSampleUsage(100L, millilitres("0"), user);

    assertEquals(3L, cached.getVersion());
  }
}
