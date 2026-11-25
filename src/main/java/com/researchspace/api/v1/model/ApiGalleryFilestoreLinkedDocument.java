package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.researchspace.model.record.RecordInformation;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A row of {@code GET /api/v1/gallery/filestores/{filestoreId}/linkedDocuments}: an ELN document or
 * an Inventory item linking the filestore item, as {@code type} says. Only {@code type} and {@code
 * ownerFullName} are set when the caller cannot read it.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiGalleryFilestoreLinkedDocument {

  private Long id;
  private String globalId;
  private String name;
  private String ownerFullName;
  private String type;

  private static final String DOCUMENT_TYPE = "DOCUMENT";

  public ApiGalleryFilestoreLinkedDocument(RecordInformation info) {
    this.id = info.getId();
    this.globalId = info.getOid() == null ? null : info.getOid().getIdString();
    this.name = info.getName();
    this.ownerFullName = info.getOwnerFullName();
    this.type = DOCUMENT_TYPE;
  }
}
