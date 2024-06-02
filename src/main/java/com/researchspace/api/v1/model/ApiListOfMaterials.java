package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.User;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.service.inventory.InventoryMaterialUsageHelper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class ApiListOfMaterials {

  @JsonProperty("id")
  Long id;

  @JsonProperty("globalId")
  String globalId;

  @Size(max = 255, message = "Name cannot be longer than 255 chars")
  @JsonProperty("name")
  String name;

  @Size(max = 255, message = "Description cannot be longer than 255 chars")
  @JsonProperty("description")
  String description;

  @JsonProperty("elnFieldId")
  Long elnFieldId;

  @JsonProperty("elnDocument")
  ApiDocumentInfo elnDocument;

  @JsonProperty("materials")
  List<ApiMaterialUsage> materials;

  public ApiListOfMaterials(ListOfMaterials lom) {
    setId(lom.getId());
    setGlobalId(lom.getOid().toString());
    setName(lom.getName());
    setDescription(lom.getDescription());
    setElnFieldId(lom.getElnField().getId());
    setMaterials(new ArrayList<>());

    for (MaterialUsage mu : lom.getMaterials()) {
      getMaterials().add(new ApiMaterialUsage(mu));
    }
  }

  public void addMaterialUsage(
      ApiInventoryRecordInfo apiInvRec,
      ApiQuantityInfo usedQuantity,
      boolean updateInventoryQuantity) {
    if (materials == null) {
      materials = new ArrayList<>();
    }
    ApiMaterialUsage newUsage = new ApiMaterialUsage(apiInvRec, usedQuantity);
    newUsage.setUpdateInventoryQuantity(updateInventoryQuantity);
    materials.add(newUsage);
  }

  public ListOfMaterials toListOfMaterials() {
    ListOfMaterials result = new ListOfMaterials();
    result.setId(getId());
    result.setName(getName());
    result.setDescription(getDescription());
    return result;
  }

  public boolean applyChangesToDatabaseListOfMaterials(
      ListOfMaterials dbLom, InventoryMaterialUsageHelper invRecHandler, User user) {

    boolean lomChanged = false;

    if (getName() != null && !getName().equals(dbLom.getName())) {
      dbLom.setName(getName());
      lomChanged = true;
    }
    if (getDescription() != null && !getDescription().equals(dbLom.getDescription())) {
      dbLom.setDescription(getDescription());
      lomChanged = true;
    }

    List<ApiMaterialUsage> incomingMaterials = getMaterials();
    if (incomingMaterials != null) {
      for (ApiMaterialUsage incomingUsage : incomingMaterials) {
        ApiInventoryRecordInfo usedApiInvRec = incomingUsage.getRecord();
        MaterialUsage existingUsage = findInvRecUsageInDbLom(usedApiInvRec, dbLom);

        QuantityInfo previousUsage = null;
        if (existingUsage != null) {
          previousUsage = existingUsage.getUsedQuantity();
          lomChanged |= incomingUsage.applyChangesToDatabaseMaterialUsage(existingUsage);
        } else {
          MaterialUsage newMaterialUsage = incomingUsage.toMaterialUsage(dbLom, invRecHandler);
          dbLom.getMaterials().add(newMaterialUsage);
          lomChanged = true;
        }

        if (lomChanged
            && incomingUsage.getUsedQuantity() != null
            && incomingUsage.isUpdateInventoryQuantity()) {
          QuantityInfo newUsage = incomingUsage.getUsedQuantity().toQuantityInfo();
          invRecHandler.updateSubSampleQuantityAfterUsage(
              usedApiInvRec, previousUsage, newUsage, user);
        }
      }

      // go through pre-existing usages, delete ones that are not among incoming ones
      Iterator<MaterialUsage> it = dbLom.getMaterials().iterator();
      while (it.hasNext()) {
        MaterialUsage dbUsage = it.next();
        boolean incomingUsageFound = false;
        for (ApiMaterialUsage incomingUsage : incomingMaterials) {
          if (incomingUsage.isMatchingMaterialUsage(dbUsage)) {
            incomingUsageFound = true;
          }
        }
        if (!incomingUsageFound) {
          it.remove();
          lomChanged = true;
        }
      }
    }
    return lomChanged;
  }

  private MaterialUsage findInvRecUsageInDbLom(
      ApiInventoryRecordInfo invRec, ListOfMaterials dbLom) {
    for (MaterialUsage usage : dbLom.getMaterials()) {
      InventoryRecord dbUsedItem = usage.getInventoryRecord();
      if (invRec.getId().equals(dbUsedItem.getId())
          && invRec.getType().toString().equals(dbUsedItem.getType().toString())) {
        return usage;
      }
    }
    return null;
  }
}
