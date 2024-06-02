package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Component helping actions on inventory list of materials. */
@Component
public class InventoryMaterialUsageHelper {

  @Autowired private InventoryRecordRetriever invRecRetriever;

  private @Autowired SubSampleApiManager subSampleMgr;
  private QuantityUtils qUtils = new QuantityUtils();

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
    subSampleMgr.registerApiSubSampleUsage((ApiSubSample) apiInvRec, usageDifference, user);
  }
}
