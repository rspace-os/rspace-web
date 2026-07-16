package com.researchspace.service.metadata;

import com.researchspace.model.User;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Everything a {@link DataCiteYamlSidecarGenerator} needs to compose a sidecar, gathered upstream.
 */
@Value
@Builder
public class SidecarGenerationContext {
  User user;

  /** Null when the user has no ORCID. */
  String orcidId;

  /** Deployment organisation name, used for creator affiliation and publisher. */
  String institutionName;

  String bucketName;

  /** Absolute prefix within the bucket; single level, no trailing slash. */
  String folderPath;

  List<SidecarFileEntry> files;
}
