/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.field.Field;
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
      "files",
      "listOfMaterials",
      "_links"
    })
public class ApiDocumentField extends ApiField {

  @JsonProperty("files")
  private List<ApiFile> files = new ArrayList<>();

  @JsonProperty("listOfMaterials")
  private List<ApiListOfMaterials> listsOfMaterials = new ArrayList<>();

  public ApiDocumentField(Field field) {
    setId(field.getId());
    setType(ApiFieldType.valueOf(field.getType().toString()));
    setColumnIndex(field.getColumnIndex());
    setName(field.getName());
    setLastModifiedMillis(field.getModificationDate());
    setGlobalId(field.getOid().toString());
    setContent(field.getFieldData());

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
