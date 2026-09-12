package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.model.User;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Component helping actions on inventory list of materials. */
@Component
public class InventoryMaterialUsageHelper {

  @Autowired private InventoryRecordRetriever invRecRetriever;

  private @Autowired SubSampleApiManager subSampleMgr;
  private @Autowired SampleSiblingRowLock siblingRowLock;
  private @Autowired InventoryPermissionUtils invPermissions;
  private QuantityUtils qUtils = new QuantityUtils();

  /**
   * Locks every distinct parent sample's subsample rows (ascending by sample id) for the subsample
   * materials named in a list-of-materials request, BEFORE any per-material decrement runs.
   *
   * <p>Every stock writer must acquire its lock groups in one canonical order: sibling sets
   * ascending by sample id, then individual rows. The sibling-set lock is row locks on all of a
   * sample's subsamples, so once the sets are held every later row lock in this transaction is a
   * re-acquisition and cannot form a deadlock cycle against the operations endpoint or a
   * Stoichiometry deduction. Without this, a list spanning several samples acquires each sample's
   * set in material-list order as the decrement loop reaches it, which can invert against another
   * writer's ascending order. Both the incoming and the stored materials are locked: an update that
   * removes a usage restores its stock, so removed subsamples' samples are written too.
   *
   * <p>Read permission is asserted on each INCOMING subsample before its parent's set is locked
   * (the same assert the mutation path applies): ids here come straight off a public request, so
   * locking first would let a caller name records they cannot access solely to lock those sibling
   * sets and delay authorized writers until the request fails (Copilot review, PR #1090). Stored
   * materials are already on the list being updated, so they carry no such assert.
   *
   * <p>PRECONDITION: a transaction is already open. This class is a plain {@code @Component} named
   * {@code *Helper}, which no XML advisor pointcut matches, so it carried no transaction advice at
   * all: called without an ambient transaction, each sibling-set lock below would have opened and
   * committed its own, releasing every lock before the loop finished and leaving the caller
   * protected by nothing. {@code Propagation.MANDATORY} makes that call fail instead. Every current
   * caller is already transactional, so this is a no-op today, which is the point (parallel review,
   * L3-L4-P5).
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockParentSampleSets(
      List<ApiMaterialUsage> incoming, List<MaterialUsage> stored, User user) {
    Set<Long> parentSampleIds = new TreeSet<>();
    if (incoming != null) {
      for (ApiMaterialUsage usage : incoming) {
        if (usage.getRecord() != null
            && ApiInventoryRecordType.SUBSAMPLE.equals(usage.getRecord().getType())) {
          InventoryRecord record = getForApiInventoryRecordInfo(usage.getRecord());
          if (record instanceof SubSample) {
            invPermissions.assertUserCanReadOrLimitedReadInventoryRecord(record, user);
            parentSampleIds.add(((SubSample) record).getSample().getId());
          }
        }
      }
    }
    if (stored != null) {
      for (MaterialUsage usage : stored) {
        if (usage.getInventoryRecord() instanceof SubSample) {
          parentSampleIds.add(((SubSample) usage.getInventoryRecord()).getSample().getId());
        }
      }
    }
    parentSampleIds.forEach(siblingRowLock::lockSiblingRowsAndRecalculateTotal);
  }

  public InventoryRecord getForApiInventoryRecordInfo(ApiInventoryRecordInfo invRecInfo) {
    return invRecRetriever.getInvRecForIdAndType(invRecInfo.getId(), invRecInfo.getType());
  }

  public void updateSubSampleQuantityAfterUsage(
      ApiInventoryRecordInfo apiInvRec,
      QuantityInfo previouslyUsedQuantity,
      QuantityInfo newUsedQuantity,
      User user) {

    if (!apiInvRec.getType().equals(ApiInventoryRecordType.SUBSAMPLE)) {
      return;
    }

    QuantityInfo usageDifference = null;
    if (previouslyUsedQuantity == null) {
      usageDifference = newUsedQuantity;
    } else {
      usageDifference = qUtils.sum(Arrays.asList(newUsedQuantity, previouslyUsedQuantity.negate()));
    }
    subSampleMgr.registerApiSubSampleUsage(apiInvRec.getId(), usageDifference, user);
  }
}
