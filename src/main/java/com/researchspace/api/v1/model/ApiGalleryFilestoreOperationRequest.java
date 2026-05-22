package com.researchspace.api.v1.model;

import com.researchspace.netfiles.ApiNfsCredentials;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/gallery/filestores/{filestoreId}/move} and {@code .../copy}.
 * The destination filestore id is in the URL path. The {@code credentials} field is optional —
 * iRODS backends require it; S3 backends ignore it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiGalleryFilestoreOperationRequest {

  @NotNull(message = "recordIds is mandatory")
  @Size(min = 1, message = "recordIds is mandatory")
  private Set<Long> recordIds;

  private ApiNfsCredentials credentials;
}
