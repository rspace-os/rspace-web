/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** A document with fields */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {
      "id",
      "globalId",
      "name",
      "created",
      "lastModified",
      "parentFolderId",
      "signed",
      "tags",
      "tagMetaData",
      "form",
      "owner",
      "fields",
      "_links"
    })
public class ApiDocument extends ApiDocumentInfo {

  @JsonProperty("fields")
  private List<ApiDocumentField> fields = new ArrayList<>();

  public ApiDocument(StructuredDocument doc, User subject) {
    super(doc, subject);
    for (Field field : doc.getFields()) {
      fields.add(new ApiDocumentField(field));
    }
  }
}
