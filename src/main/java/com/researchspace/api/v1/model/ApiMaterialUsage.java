package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.inventory.InventoryMaterialUsageHelper;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApiMaterialUsage {

  @JsonProperty("invRec")
  ApiInventoryRecordInfo record;

  @JsonProperty("usedQuantity")
  ApiQuantityInfo usedQuantity;

  @JsonProperty(value = "updateInventoryQuantity", access = Access.WRITE_ONLY)
  private boolean updateInventoryQuantity;

  public ApiMaterialUsage(ApiInventoryRecordInfo record, ApiQuantityInfo usedQuantity) {
    this.record = record;
    this.usedQuantity = usedQuantity;
  }

  public ApiMaterialUsage(MaterialUsage mu) {
    record = ApiInventoryRecordInfo.fromInventoryRecord(mu.getInventoryRecord());
    if (mu.getUsedQuantity() != null) {
      usedQuantity = new ApiQuantityInfo(mu.getUsedQuantity());
    }
  }

  public boolean applyChangesToDatabaseMaterialUsage(MaterialUsage existingUsage) {
    if (usedQuantity != null
        && !usedQuantity.toQuantityInfo().equals(existingUsage.getUsedQuantity())) {
      existingUsage.setUsedQuantity(usedQuantity.toQuantityInfo());
      return true;
    }
    return false;
  }

  public boolean isMatchingMaterialUsage(MaterialUsage dbUsage) {
    InventoryRecord dbUsageInvRec = dbUsage.getInventoryRecord();
    if (record != null
        && record.getType().toString().equals(dbUsageInvRec.getType().toString())
        && dbUsageInvRec.getId().equals(record.getId())) {
      return true;
    }
    return false;
  }

  public MaterialUsage toMaterialUsage(
      ListOfMaterials dbLom, InventoryMaterialUsageHelper invRecRetriever) {
    InventoryRecord invRec = invRecRetriever.getForApiInventoryRecordInfo(record);
    MaterialUsage result = new MaterialUsage(dbLom, invRec, null);
    if (getUsedQuantity() != null) {
      result.setUsedQuantity(getUsedQuantity().toQuantityInfo());
    }
    return result;
  }
}
