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
  /** The user generating the sidecar; becomes the sole creator in phase 1. */
  User user;

  /** The user's ORCID iD, or null when they have none. */
  String orcidId;

  /** Organisation name for the deployment, used for creator affiliation and publisher. */
  String institutionName;

  String bucketName;

  /** Absolute folder prefix (single level) within the bucket, e.g. {@code XRD-Experiments}. */
  String folderPath;

  List<SidecarFileEntry> files;
}
