package com.researchspace.netfiles;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Value;

/**
 * Identifies the RSpace user responsible for a filestore write. Used to populate backend-native
 * audit metadata (e.g. S3 {@code x-amz-meta-*} object metadata) so that, when a single shared IAM
 * identity performs writes for many RSpace users, every object still carries the originating user's
 * name in its own attributes.
 */
@Value
public class WriteAttribution {

  private static final String META_USER = "rspace-user";
  private static final String META_RECORD_ID = "rspace-record-id";
  private static final String META_RECORD_NAME = "rspace-record-name";

  String username;

  /**
   * Maps each RSpace record id to its display name. Null when there is no record context (e.g. for
   * {@code /transfer}).
   */
  Map<Long, String> recordNames;

  /**
   * Builds the per-object metadata map for an upload of the given RSpace record. When {@code
   * recordId} is null (e.g. for {@code /transfer}, which has no record context) the {@code
   * rspace-record-id} and {@code rspace-record-name} keys are omitted.
   */
  public Map<String, String> metadataForRecord(Long recordId) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put(META_USER, username);
    if (recordId != null) {
      m.put(META_RECORD_ID, recordId.toString());
      if (recordNames != null) {
        String name = recordNames.get(recordId);
        if (name != null) {
          m.put(META_RECORD_NAME, name);
        }
      }
    }
    return m;
  }
}
