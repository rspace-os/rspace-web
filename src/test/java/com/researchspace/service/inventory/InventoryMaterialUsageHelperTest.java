package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The sibling-set lock hoist for List of Materials requests. Every stock writer must acquire its
 * lock groups in one canonical order (sibling sets ascending by sample id, then rows); a list
 * spanning several samples would otherwise take each sample's set in material-list order as the
 * decrement loop reaches it, which can invert against another writer and deadlock.
 */
@ExtendWith(MockitoExtension.class)
public class InventoryMaterialUsageHelperTest {

  @Mock private InventoryRecordRetriever invRecRetriever;
  @Mock private SubSampleApiManager subSampleMgr;
  @Mock private SampleSiblingRowLock siblingRowLock;
  @Mock private InventoryPermissionUtils invPermissions;
  @InjectMocks private InventoryMaterialUsageHelper helper;

  private final User user = new User("anyUser");

  private SubSample subSampleUnderSample(long subSampleId, long sampleId) {
    Sample parent = new Sample();
    parent.setId(sampleId);
    SubSample subSample = new SubSample();
    subSample.setId(subSampleId);
    subSample.setSample(parent);
    return subSample;
  }

  private ApiMaterialUsage usageOfSubSample(long subSampleId, long sampleId) {
    ApiSubSample record = new ApiSubSample();
    record.setId(subSampleId);
    when(invRecRetriever.getInvRecForIdAndType(subSampleId, ApiInventoryRecordType.SUBSAMPLE))
        .thenReturn(subSampleUnderSample(subSampleId, sampleId));
    return new ApiMaterialUsage(record, null);
  }

  @Test
  public void locksDistinctParentSampleSetsAscendingAcrossIncomingAndStored() {
    // incoming usages submitted highest-sample-first, plus a duplicate of one parent; the stored
    // materials contribute the lowest sample id (an update that removes a usage restores its
    // stock, so removed subsamples' samples are written too)
    ApiMaterialUsage ninety = usageOfSubSample(900L, 90L);
    ApiMaterialUsage eighty = usageOfSubSample(800L, 80L);
    ApiMaterialUsage eightyAgain = usageOfSubSample(801L, 80L);
    MaterialUsage stored = mock(MaterialUsage.class);
    when(stored.getInventoryRecord()).thenReturn(subSampleUnderSample(700L, 70L));

    helper.lockParentSampleSets(List.of(ninety, eighty, eightyAgain), List.of(stored), user);

    InOrder inOrder = inOrder(siblingRowLock);
    inOrder.verify(siblingRowLock).lockSiblingRowsAndRecalculateTotal(70L);
    inOrder.verify(siblingRowLock).lockSiblingRowsAndRecalculateTotal(80L);
    inOrder.verify(siblingRowLock).lockSiblingRowsAndRecalculateTotal(90L);
    // deduped: one set lock per sample, however many of its subsamples the list names
    verify(siblingRowLock, times(1)).lockSiblingRowsAndRecalculateTotal(80L);
  }

  @Test
  public void locksNothingForNonSubSampleMaterialsOrEmptyRequests() {
    // a sample-typed material holds no subsample stock to decrement, so no set lock is taken
    ApiSample sampleRecord = new ApiSample();
    sampleRecord.setId(90L);

    helper.lockParentSampleSets(List.of(new ApiMaterialUsage(sampleRecord, null)), null, user);
    helper.lockParentSampleSets(null, null, user);

    verifyNoInteractions(siblingRowLock);
  }

  @Test
  public void assertsReadPermissionOnEachIncomingSubSampleBeforeLockingAnything() {
    // The ids come straight off a public request: without this assert a caller could name records
    // they cannot access solely to lock those sibling sets and delay authorized writers until the
    // request fails later (Copilot review, PR #1090).
    ApiMaterialUsage usage = usageOfSubSample(900L, 90L);
    doThrow(new RuntimeException("no permission"))
        .when(invPermissions)
        .assertUserCanReadOrLimitedReadInventoryRecord(any(SubSample.class), eq(user));

    assertThrows(
        RuntimeException.class, () -> helper.lockParentSampleSets(List.of(usage), null, user));

    verifyNoInteractions(siblingRowLock);
  }
}
