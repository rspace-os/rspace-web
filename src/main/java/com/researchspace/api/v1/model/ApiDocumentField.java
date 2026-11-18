/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.dtos.FieldDisplayRuleDTO;
import com.researchspace.model.field.ElnFieldRules;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.IFieldForm;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** A field in ApiDocument, with a list of attached Files. Also inherited by field in ApiSample. */
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
      "content",
      "lastModified",
      "columnIndex",
      "fieldKey",
      "displayRule",
      "files",
      "listOfMaterials",
      "_links"
    })
public class ApiDocumentField extends ApiField {

  @JsonProperty("files")
  private List<ApiFile> files = new ArrayList<>();

  @JsonProperty("listOfMaterials")
  private List<ApiListOfMaterials> listsOfMaterials = new ArrayList<>();

  /**
   * The key of this field's form field, in the form version the document was created from, so a
   * client can evaluate display rules from the document alone.
   */
  @JsonProperty("fieldKey")
  private String fieldKey;

  /** The form field's display rule in that version, or null when the field is always shown. */
  @JsonProperty("displayRule")
  private FieldDisplayRuleDTO displayRule;

  public ApiDocumentField(Field field) {
    setId(field.getId());
    setType(ApiFieldType.valueOf(field.getType().toString()));
    setColumnIndex(field.getColumnIndex());
    setName(field.getName());
    setLastModifiedMillis(field.getModificationDate());
    setGlobalId(field.getOid().toString());
    setContent(field.getFieldData());
    IFieldForm formField = field.getFieldForm();
    if (formField != null) {
      setFieldKey(formField.getFieldKey());
      setDisplayRule(FieldDisplayRuleDTO.from(ElnFieldRules.storedRule(formField)));
    }

    addLinkedMediaFiles(field);
    addLinkedListsOfMaterials(field);
  }

  private void addLinkedMediaFiles(Field field) {
    field.getLinkedMediaFiles().stream()
        .filter(media -> !media.isDeleted())
        .forEach(media -> files.add(new ApiFile(media.getMediaFile())));
  }

  private void addLinkedListsOfMaterials(Field field) {
    field.getListsOfMaterials().stream()
        .forEach(lom -> listsOfMaterials.add((new ApiListOfMaterials(lom))));
  }
}
