package com.researchspace.api.v1.model;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for the sidecar preview/save endpoints (S3 only). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiGalleryFilestoreSidecarRequest {

  /** Folder the sidecar describes, relative to the filestore root. Empty/"/" means the root. */
  @NotNull(message = "path is mandatory")
  private String path;
}
