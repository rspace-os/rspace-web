package com.researchspace.api.v1.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for the sidecar endpoints: the target filename and the serialized sidecar content
 * (YAML). For {@code preview} nothing is written; for the save endpoint this is the content that
 * was stored in the filestore.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiGalleryFilestoreSidecar {
  private String filename;
  private String content;
}
