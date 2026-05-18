package com.researchspace.netfiles;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Value;

/**
 * Identifies the RSpace user and operation responsible for a filestore write. Used to populate
 * backend-native audit metadata (e.g. S3 {@code x-amz-meta-*} object metadata) so that, when a
 * single shared IAM identity performs writes for many RSpace users, every object still carries the
 * originating user's name in its own attributes.
 */
@Value
public class WriteAttribution {

  private static final String META_USER = "rspace-user";
  private static final String META_OP = "rspace-op";
  private static final String META_RECORD_ID = "rspace-record-id";

  String username;
  String operation;

  /**
   * Builds the per-object metadata map for an upload of the given RSpace record. When {@code
   * recordId} is null (e.g. for {@code /transfer}, which has no record context) the {@code
   * rspace-record-id} key is omitted.
   */
  public Map<String, String> metadataForRecord(Long recordId) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put(META_USER, username);
    m.put(META_OP, operation);
    if (recordId != null) {
      m.put(META_RECORD_ID, recordId.toString());
    }
    return m;
  }
}
