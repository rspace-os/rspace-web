package com.researchspace.b2inst.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single draft file entry, shared across the draft file endpoints.
 *
 * <p>It appears in the declared-files listing and as the standalone upload response while
 * {@code pending}, then once {@code completed}, at which
 * point the upload fields ({@link #checksum}, {@link #mimetype}, {@link #size},
 * {@link #fileId}, {@link #versionId}, {@link #bucketId}, {@link #storageClass})
 * are populated. The same type also represents the committed entries listed on a
 * {@link B2instRecordFiles} container.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instDraftFile {

  /** File name (key) the bytes are stored under. */
  @JsonProperty("key")
  private String key;

  /** Creation timestamp of the entry. */
  @JsonProperty("created")
  private String created;

  /** Last-update timestamp of the entry. */
  @JsonProperty("updated")
  private String updated;

  /** Upload status: {@code "pending"} before commit, {@code "completed"} after. */
  @JsonProperty("status")
  private String status;

  /** Optional per-file metadata; {@code null} unless set. */
  @JsonProperty("metadata")
  private Map<String, Object> metadata;

  /** Checksum of the committed bytes, for example {@code "md5:..."} (completed only). */
  @JsonProperty("checksum")
  private String checksum;

  /** Detected MIME type, for example {@code "image/png"} (completed only). */
  @JsonProperty("mimetype")
  private String mimetype;

  /** Size of the committed file in bytes (completed only). */
  @JsonProperty("size")
  private Long size;

  /** Internal file object id (completed only). */
  @JsonProperty("file_id")
  private String fileId;

  /** Internal object-version id (completed only). */
  @JsonProperty("version_id")
  private String versionId;

  /** Internal storage bucket id (completed only). */
  @JsonProperty("bucket_id")
  private String bucketId;

  /** Storage class of the file (completed only). */
  @JsonProperty("storage_class")
  private String storageClass;

  /** Per-entry links: content, self and commit. */
  @JsonProperty("links")
  private B2instFileLinks links;
}
