package com.researchspace.netfiles;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Value;

/**
 * Identifies the RSpace user responsible for a filestore write, and when it happened. Used to
 * populate backend-native audit metadata (e.g. S3 {@code x-amz-meta-*} object metadata) so that,
 * when a single shared IAM identity performs writes for many RSpace users, every object still
 * carries the originating user's name and creation time in its own attributes. These drive the
 * creator/age delete gate (only the {@code rspace-created-by} user may delete an object, and only
 * within the configured window after {@code rspace-created-at}).
 */
@Value
public class WriteAttribution {

  public static final String META_CREATED_BY = "rspace-created-by";
  public static final String META_CREATED_AT = "rspace-created-at";
  private static final String META_RECORD_ID = "rspace-record-id";
  private static final String META_RECORD_NAME = "rspace-record-name";

  String username;

  /**
   * Maps each RSpace record id to its display name. Null when there is no record context (e.g. for
   * {@code /transfer}).
   */
  Map<Long, String> recordNames;

  /** The write/creation time, stamped as the {@code rspace-created-at} ISO-8601 instant. */
  Instant createdAt;

  /**
   * Builds the per-object metadata map for an upload of the given RSpace record. Always includes
   * {@code rspace-created-by} and {@code rspace-created-at}. When {@code recordId} is null (e.g.
   * for {@code /transfer} or folder creation, which have no record context) the {@code
   * rspace-record-id} and {@code rspace-record-name} keys are omitted.
   */
  public Map<String, String> metadataForRecord(Long recordId) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put(META_CREATED_BY, username);
    if (createdAt != null) {
      m.put(META_CREATED_AT, createdAt.toString());
    }
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
