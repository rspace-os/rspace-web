/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** API representation of an Inventory ApiInstrumentEntity */
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
  "canBeDeleted",
  "deleted",
  "deletedDate",
  "iconId",
  "tags",
  "type",
  "attachments",
  "barcodes",
  "identifiers",
  "owner",
  "permittedActions",
  "sharingMode",
  "templateId",
  "templateVersion",
  "template",
  "revisionId",
  "version",
  "historicalVersion",
  "fields",
  "extraFields",
  "sharedWith",
  "_links"
})
public abstract class ApiInstrumentEntity extends ApiInstrumentEntityInfo {

  @JsonProperty("fields")
  protected List<ApiInventoryEntityField> fields = new ArrayList<>();

  @JsonProperty("extraFields")
  protected List<ApiExtraField> extraFields = new ArrayList<>();

  @JsonProperty(value = "sharedWith")
  private List<ApiGroupInfoWithSharedFlag> sharedWith;

  @JsonProperty(value = "canBeDeleted")
  private Boolean canBeDeleted;

  public ApiInstrumentEntity(InstrumentEntity instrumentEntity) {
    super(instrumentEntity);
    for (InventoryEntityField field : instrumentEntity.getActiveFields()) {
      fields.add(new ApiInventoryEntityField(field));
    }
    for (ExtraField extraField : instrumentEntity.getActiveExtraFields()) {
      extraFields.add(new ApiExtraField(extraField));
    }
    sharedWith = new ArrayList<>();
  }

  /**
   * @param dbInstrumentEntity db entity to which incoming changes should be applied
   * @return if any change was applied
   */
  public boolean applyChangesToDatabaseInstrument(InstrumentEntity dbInstrumentEntity, User user) {
    boolean contentChanged = super.applyChangesToDatabaseInstrument(dbInstrumentEntity);

    if (fields != null) {
      List<ApiInventoryEntityField> modifiedFields =
          fields.stream()
              .filter(f -> !(f.isNewFieldRequest() || f.isDeleteFieldRequest()))
              .collect(Collectors.toList());

      for (ApiInventoryEntityField field : modifiedFields) {
        if (field.getId() == null) {
          throw new IllegalArgumentException("'id' property not provided for a field");
        }
        Optional<InventoryEntityField> dbFieldOpt =
            dbInstrumentEntity.getActiveFields().stream()
                .filter(sf -> Objects.equals(sf.getId(), field.getId()))
                .findFirst();
        if (!dbFieldOpt.isPresent()) {
          throw new IllegalArgumentException(
              "Field id: "
                  + field.getId()
                  + " doesn't match any of the field ids of current instrument");
        }
        InventoryEntityField dbField = dbFieldOpt.get();
        if (dbInstrumentEntity.isTemplate()) {
          contentChanged |= field.applyChangesToDatabaseTemplateField(dbField, user);
        } else {
          contentChanged |= field.applyChangesToDatabaseField(dbField, user);
        }
      }
    }
    contentChanged |=
        applyChangesToDatabaseExtraFields(
            extraFields, dbInstrumentEntity.getActiveExtraFields(), user);
    contentChanged |=
        applyChangesToDatabaseBarcodes(getBarcodes(), dbInstrumentEntity.getActiveBarcodes());
    contentChanged |=
        applyChangesToDatabaseIdentifiers(
            getIdentifiers(), dbInstrumentEntity.getActiveIdentifiers(), user);
    contentChanged |= applyChangesToSharingMode(dbInstrumentEntity, user);

    return contentChanged;
  }

  @Override
  protected void nullifyListsForLimitedView(ApiInventoryRecordInfo apiInvRec) {
    super.nullifyListsForLimitedView(apiInvRec);
    ApiInstrument apiInstrument = (ApiInstrument) apiInvRec;
    apiInstrument.setFields(null);
    apiInstrument.setExtraFields(null);
    apiInstrument.setSharedWith(null);
  }
}
