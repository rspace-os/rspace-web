package com.researchspace.service.metadata;

import com.researchspace.model.User;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Everything a {@link DataCiteYamlSidecarFileGenerator} needs to compose a sidecar, gathered
 * upstream.
 */
@Value
@Builder
public class SidecarFileGenerationContext {
  User user;

  /** Null when the user has no ORCID. */
  String orcidId;

  /** Deployment organisation name, used for creator affiliation and publisher. */
  String institutionName;

  /** System-configured ROR id for the organisation, or null when the sysadmin has not set one. */
  String rorId;

  String bucketName;

  /** Absolute prefix within the bucket; no leading or trailing slash. */
  String folderPath;

  List<SidecarFileEntry> files;

  /**
   * Global ID page URLs of the Linked documents the user can read, distinct, by ascending id; null
   * or empty when none.
   */
  List<String> linkedDocumentUrls;

  /**
   * Global ID page URL and record-side relation of each Inventory item linking the folder or one of
   * its files that the user can read, distinct; null or empty when none.
   */
  List<SidecarRelatedIdentifier> linkingItemIdentifiers;
}
