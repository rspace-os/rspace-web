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

/** Component helping actions on inventory list of materials. */
@Component
public class InventoryMaterialUsageHelper {

  @Autowired private InventoryRecordRetriever invRecRetriever;

  private @Autowired SubSampleApiManager subSampleMgr;
  private @Autowired SampleApiManager sampleApiMgr;
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
   */
  public void lockParentSampleSets(List<ApiMaterialUsage> incoming, List<MaterialUsage> stored) {
    Set<Long> parentSampleIds = new TreeSet<>();
    if (incoming != null) {
      for (ApiMaterialUsage usage : incoming) {
        if (usage.getRecord() != null
            && ApiInventoryRecordType.SUBSAMPLE.equals(usage.getRecord().getType())) {
          InventoryRecord record = getForApiInventoryRecordInfo(usage.getRecord());
          if (record instanceof SubSample) {
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
    parentSampleIds.forEach(sampleApiMgr::recalculateTotalFromLockedRows);
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
