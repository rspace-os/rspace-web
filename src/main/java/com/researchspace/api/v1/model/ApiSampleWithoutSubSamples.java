/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.SampleField;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** API representation of an Inventory Sample with all data apart from SubSamples. */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
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
  "templateId",
  "templateVersion",
  "template",
  "revisionId",
  "version",
  "historicalVersion",
  "subSampleAlias",
  "subSamplesCount",
  "storageTempMin",
  "storageTempMax",
  "sampleSource",
  "expiryDate",
  "sharedWith",
  "fields",
  "extraFields",
  "_links"
})
public class ApiSampleWithoutSubSamples extends ApiSampleInfo {

  @JsonProperty("fields")
  protected List<ApiSampleField> fields = new ArrayList<>();

  @JsonProperty("extraFields")
  protected List<ApiExtraField> extraFields = new ArrayList<>();

  @JsonProperty(value = "sharedWith")
  private List<ApiGroupInfoWithSharedFlag> sharedWith;

  protected ApiSampleWithoutSubSamples(Sample sample) {
    super(sample);

    for (SampleField field : sample.getActiveFields()) {
      fields.add(new ApiSampleField(field));
    }
    for (ExtraField extraField : sample.getActiveExtraFields()) {
      extraFields.add(new ApiExtraField(extraField));
    }
    sharedWith = new ArrayList<>();
  }

  @Override
  public List<ApiInventoryFile> getAllAttachments() {
    if (super.getAllAttachments() == null) {
      return null; // must be record view that doesn't include attachments
    }

    List<ApiInventoryFile> allAttachments = new ArrayList<>(super.getAllAttachments());
    for (ApiSampleField sf : getFields()) {
      if (sf.getAttachment() != null) {
        allAttachments.add(sf.getAttachment());
      }
    }
    return allAttachments;
  }

  /**
   * @param dbSample db entity to which incoming changes should be applied
   * @return if any change was applied
   */
  public boolean applyChangesToDatabaseSample(Sample dbSample, User user) {
    boolean contentChanged = super.applyChangesToDatabaseSample(dbSample);

    if (fields != null) {
      List<ApiSampleField> modifiedFields =
          fields.stream()
              .filter(f -> !(f.isNewFieldRequest() || f.isDeleteFieldRequest()))
              .collect(Collectors.toList());

      for (ApiSampleField field : modifiedFields) {
        if (field.getId() == null) {
          throw new IllegalArgumentException("'id' property not provided for a field");
        }
        Optional<SampleField> dbFieldOpt =
            dbSample.getActiveFields().stream()
                .filter(sf -> Objects.equals(sf.getId(), field.getId()))
                .findFirst();
        if (!dbFieldOpt.isPresent()) {
          throw new IllegalArgumentException(
              "Field id: "
                  + field.getId()
                  + " doesn't match any of the field ids of current sample");
        }
        SampleField dbField = dbFieldOpt.get();
        if (dbSample.isTemplate()) {
          contentChanged |= field.applyChangesToDatabaseTemplateField(dbField, user);
        } else {
          contentChanged |= field.applyChangesToDatabaseField(dbField, user);
        }
      }
      // applyFieldOrderingChanges(modifiedFields, dbSample);
    }
    contentChanged |=
        applyChangesToDatabaseExtraFields(extraFields, dbSample.getActiveExtraFields(), user);
    contentChanged |= applyChangesToDatabaseBarcodes(getBarcodes(), dbSample.getActiveBarcodes());
    contentChanged |=
        applyChangesToDatabaseIdentifiers(getIdentifiers(), dbSample.getActiveIdentifiers(), user);
    contentChanged |= applyChangesToSharingMode(dbSample, user);

    return contentChanged;
  }

  @Override
  protected void nullifyListsForLimitedView(ApiInventoryRecordInfo apiInvRec) {
    super.nullifyListsForLimitedView(apiInvRec);
    ApiSampleWithoutSubSamples apiSample = (ApiSampleWithoutSubSamples) apiInvRec;
    apiSample.setExtraFields(null);
    apiSample.setFields(null);
    apiSample.setSharedWith(null);
  }
}
