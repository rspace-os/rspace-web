/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.SubSampleNote;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.units.RSUnitDef;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** API representation of an Inventory SubSample */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonPropertyOrder({
  "id",
  "globalId",
  "name",
  "description",
  "created",
  "createdBy",
  "lastModified",
  "modifiedBy",
  "modifiedByFullName",
  "deleted",
  "deletedDate",
  "iconId",
  "quantity",
  "tags",
  "type",
  "attachments",
  "barcodes",
  "identifiers",
  "owner",
  "permittedActions",
  "sharingMode",
  "sharedWith",
  "parentContainers",
  "parentLocation",
  "lastNonWorkbenchParent",
  "lastMoveDate",
  "deletedOnSampleDeletion",
  "revisionId",
  "sample",
  "extraFields",
  "notes",
  "_links"
})
public class ApiSubSample extends ApiSubSampleInfoWithSampleInfo {

  @JsonProperty("extraFields")
  private List<ApiExtraField> extraFields = new ArrayList<>();

  @JsonProperty("notes")
  private List<ApiSubSampleNote> notes = new ArrayList<>();

  public ApiSubSample(SubSample subSample) {
    super(subSample);

    for (ExtraField extraField : subSample.getActiveExtraFields()) {
      extraFields.add(new ApiExtraField(extraField));
    }
    for (SubSampleNote note : subSample.getNotes()) {
      notes.add(new ApiSubSampleNote(note));
    }
  }

  /** to simplify creation of named subsample */
  public ApiSubSample(String name) {
    setName(name);
  }

  public boolean applyChangesToDatabaseSubSample(SubSample dbSubSample, User user) {
    boolean contentChanged = super.applyChangesToDatabaseInventoryRecord(dbSubSample);
    contentChanged |= updateQuantity(dbSubSample);
    contentChanged |=
        applyChangesToDatabaseExtraFields(extraFields, dbSubSample.getActiveExtraFields(), user);
    contentChanged |=
        applyChangesToDatabaseBarcodes(getBarcodes(), dbSubSample.getActiveBarcodes());
    contentChanged |=
        applyChangesToDatabaseIdentifiers(
            getIdentifiers(), dbSubSample.getActiveIdentifiers(), user);
    return contentChanged;
  }

  private boolean updateQuantity(SubSample dbSubSample) {
    if (getQuantity() != null) {
      if (dbSubSample.getQuantity() != null) {
        RSUnitDef dbUnit = RSUnitDef.getUnitById(dbSubSample.getQuantity().getUnitId());
        RSUnitDef incomingUnit = RSUnitDef.getUnitById(getQuantity().getUnitId());
        if (!dbUnit.isComparable(incomingUnit)) {
          throw new IllegalArgumentException(
              String.format(
                  "Incoming unit %s is incompatible with stored unit %s",
                  incomingUnit.getId(), dbUnit.getId()));
        }
      }
      if (!getQuantity().toQuantityInfo().equals(dbSubSample.getQuantity())) {
        dbSubSample.setQuantity(getQuantity().toQuantityInfo());
        return true;
      }
    }
    return false;
  }

  @Override
  protected void nullifyListsForLimitedView(ApiInventoryRecordInfo apiInvRec) {
    ApiSubSample apiSample = (ApiSubSample) apiInvRec;
    super.nullifyListsForLimitedView(apiSample);
    apiSample.setExtraFields(null);
    apiSample.setNotes(null);
  }
}
