package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.inventory.field.InventoryLink;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A link target that is a file or folder inside a filestore (ADR 0016). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"filestoreId", "remotePath"})
public class ApiInventoryLinkFilestoreTarget {

  private Long filestoreId;

  private String remotePath;

  /** {@code <filestoreId>:<remotePath>}, the notation filestore links use in documents. */
  public String asLogicPath() {
    return filestoreId + ":" + remotePath;
  }

  public boolean namesAnItem() {
    return filestoreId != null && !"/".equals(InventoryLink.canonicalRemotePath(remotePath));
  }
}
