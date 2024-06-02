/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.User;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryChoiceFieldDef;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import com.researchspace.model.inventory.field.SampleField;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** A field in a Document, with a list of attached Files */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
@JsonPropertyOrder(
    value = {
      "id",
      "globalId",
      "name",
      "type",
      "mandatory",
      "content",
      "lastModified",
      "columnIndex",
      "definition",
      "selectedOptions",
      "attachment",
      "_links"
    })
public class ApiSampleField extends ApiField {

  @JsonProperty("attachment")
  private ApiInventoryFile attachment;

  @JsonProperty("definition")
  private ApiInventoryFieldDef definition;

  @JsonProperty("selectedOptions")
  private List<String> selectedOptions;

  @JsonProperty("mandatory")
  private Boolean mandatory;

  @JsonProperty(value = "newFieldRequest", access = Access.WRITE_ONLY)
  private boolean newFieldRequest;

  @JsonProperty(value = "deleteFieldRequest", access = Access.WRITE_ONLY)
  private boolean deleteFieldRequest;

  @JsonProperty(value = "deleteFieldOnSampleUpdate", access = Access.WRITE_ONLY)
  private boolean deleteFieldOnSampleUpdate;

  /** Embeds choice/radio definitions in the ApiField */
  @Data
  @NoArgsConstructor
  public static class ApiInventoryFieldDef {
    private List<String> options = new ArrayList<>();
    private boolean multiple;

    public ApiInventoryFieldDef(List<String> options, boolean multiple) {
      this.options = options;
      this.multiple = multiple;
    }

    ApiInventoryFieldDef(InventoryChoiceField def) {
      this(def.getAllOptions(), def.isMultipleChoice());
    }

    ApiInventoryFieldDef(InventoryRadioField def) {
      this(def.getAllOptions(), false);
    }

    public boolean applyChangesToRadioDefinition(InventoryRadioFieldDef def) {
      boolean changed = false;
      if (!getOptions().isEmpty() && !getOptions().equals(def.getRadioOptionsList())) {
        def.setRadioOptionsList(getOptions());
        changed = true;
      }
      return changed;
    }

    public boolean applyChangesToChoiceDefinition(InventoryChoiceFieldDef def) {
      boolean changed = false;
      if (!getOptions().isEmpty() && !getOptions().equals(def.getChoiceOptionsList())) {
        def.setChoiceOptionsList(getOptions());
        changed = true;
      }
      return changed;
    }
  }

  public ApiSampleField(SampleField field) {
    setId(field.getId());
    setType(ApiFieldType.valueOf(field.getType().toString()));
    setColumnIndex(field.getColumnIndex());
    setName(field.getName());
    setLastModifiedMillis(field.getModificationDate());
    if (field.getId() != null) {
      setGlobalId(field.getOid().toString());
    }
    setMandatory(field.isMandatory());

    if (FieldType.CHOICE.equals(field.getType())) {
      InventoryChoiceField icf = (InventoryChoiceField) field;
      setDefinition(new ApiInventoryFieldDef(icf));
    } else if (FieldType.RADIO.equals(field.getType())) {
      InventoryRadioField irf = (InventoryRadioField) field;
      setDefinition(new ApiInventoryFieldDef(irf));
    } else if (FieldType.ATTACHMENT.equals(field.getType())) {
      if (field.getAttachedFile() != null) {
        setAttachment(new ApiInventoryFile(field.getAttachedFile()));
      }
    }

    if (field.isOptionsStoringField()) {
      setSelectedOptions(field.getSelectedOptions());
    } else {
      setContent(field.getFieldData());
    }
  }

  @JsonIgnore
  public boolean isOptionsStoringField() {
    return getType() != null
        && (ApiFieldType.RADIO.equals(getType()) || ApiFieldType.CHOICE.equals(getType()));
  }

  /** Method for modifying instance of sample field. Currently only updates field content. */
  public boolean applyChangesToDatabaseField(SampleField dbField, User user) {
    boolean contentChanged = applyContentChangesToDbField(dbField);
    if (contentChanged) {
      dbField.setModificationDate(new Date().getTime());
    }
    return contentChanged;
  }

  /** Method for modifying instance of template field. Updates field's content and meta-data. */
  public boolean applyChangesToDatabaseTemplateField(SampleField dbField, User user) {
    boolean contentChanged = false;
    if (getName() != null) {
      if (!getName().equals(dbField.getName())) {
        dbField.setName(getName());
        contentChanged = true;
      }
    }
    if (getColumnIndex() != null) {
      if (!getColumnIndex().equals(dbField.getColumnIndex())) {
        dbField.setColumnIndex(getColumnIndex());
        contentChanged = true;
      }
    }
    if (getDefinition() != null) {
      boolean definitionChanged = false;
      if (dbField instanceof InventoryRadioField) {
        InventoryRadioField dbRadioField = (InventoryRadioField) dbField;
        InventoryRadioFieldDef dbRadioDefCopy = dbRadioField.getRadioDef().shallowCopy();
        definitionChanged = getDefinition().applyChangesToRadioDefinition(dbRadioDefCopy);
        if (definitionChanged) {
          dbRadioField.setRadioDef(dbRadioDefCopy);
          updateSelectedOptionsToNewRadioDef(dbRadioField);
        }
      } else if (dbField instanceof InventoryChoiceField) {
        InventoryChoiceField dbChoiceField = (InventoryChoiceField) dbField;
        InventoryChoiceFieldDef dbChoiceDefCopy = dbChoiceField.getChoiceDef().shallowCopy();
        definitionChanged = getDefinition().applyChangesToChoiceDefinition(dbChoiceDefCopy);
        if (definitionChanged) {
          dbChoiceField.setChoiceDef(dbChoiceDefCopy);
          updateSelectedOptionsToNewChoiceDef(dbChoiceField);
        }
      }
      contentChanged |= definitionChanged;
    }
    if (getMandatory() != null) {
      if (!getMandatory().equals(dbField.isMandatory())) {
        dbField.setMandatory(getMandatory());
        contentChanged = true;
      }
    }

    contentChanged |= applyContentChangesToDbField(dbField);

    if (contentChanged) {
      dbField.setModificationDate(new Date().getTime());
    }
    return contentChanged;
  }

  private void updateSelectedOptionsToNewRadioDef(InventoryRadioField dbRadioField) {
    List<String> selectedOptions = dbRadioField.getSelectedOptions();
    if (!selectedOptions.isEmpty()) {
      String selectedRadioOption = selectedOptions.get(0);
      if (!dbRadioField.getAllOptions().contains(selectedRadioOption)) {
        dbRadioField.setSelectedOptions(null);
      }
    }
  }

  private void updateSelectedOptionsToNewChoiceDef(InventoryChoiceField dbChoiceField) {
    List<String> selectedOptions = dbChoiceField.getSelectedOptions();
    List<String> newSelectedOptions = new ArrayList<>();
    for (String selectedChoiceOption : selectedOptions) {
      if (dbChoiceField.getAllOptions().contains(selectedChoiceOption)) {
        newSelectedOptions.add(selectedChoiceOption);
      }
    }
    dbChoiceField.setSelectedOptions(newSelectedOptions);
  }

  private boolean applyContentChangesToDbField(SampleField dbField) {
    boolean contentChanged = false;

    // for choice/radio the content comes/goes through selectedOptions
    boolean isOptionsField = dbField.isOptionsStoringField();

    if (!isOptionsField && getContent() != null) {
      if (!getContent().equals(dbField.getData())) {
        dbField.setFieldData(getContent());
        contentChanged = true;
      }
    }
    if (isOptionsField && getSelectedOptions() != null) {
      if (!getSelectedOptions().equals(dbField.getSelectedOptions())) {
        dbField.setSelectedOptions(getSelectedOptions());
        contentChanged = true;
      }
    }
    return contentChanged;
  }
}
