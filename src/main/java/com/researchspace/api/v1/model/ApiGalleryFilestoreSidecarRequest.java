package com.researchspace.api.v1.model;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for the sidecar endpoints {@code POST
 * /api/v1/gallery/filestores/{filestoreId}/sidecar/preview} and {@code POST
 * /api/v1/gallery/filestores/{filestoreId}/sidecar}. Generates a metadata sidecar for the folder at
 * {@code path} (a filestore-relative path; {@code ""} or {@code "/"} for the filestore root). S3
 * only.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiGalleryFilestoreSidecarRequest {

  /** Folder the sidecar describes, relative to the filestore root. Empty/"/" means the root. */
  @NotNull(message = "path is mandatory")
  private String path;
}
