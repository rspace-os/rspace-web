package com.researchspace.b2inst.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.b2inst.model.common.B2instAccess;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of {@code POST /api/records}: the freshly created draft record.
 *
 * <p>The connector reads {@link #id} from this response and threads it through the
 * later calls as the record id (RID). The commonly used fields are modelled
 * explicitly; the heavier bookkeeping blocks ({@link #parent}, {@link #versions},
 * {@link #pids}, {@link #stats}, {@link #customFields}, {@link #deletionStatus})
 * are kept loosely typed so the wrapper stays faithful without a large number of
 * rarely used classes.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instDraftRecord {

  /** Record id (RID) assigned by the server; needed by every subsequent step. */
  @JsonProperty("id")
  private String id;

  /** Whether this representation is a draft. */
  @JsonProperty("is_draft")
  private Boolean isDraft;

  /** Whether the record has been published. */
  @JsonProperty("is_published")
  private Boolean isPublished;

  /** Lifecycle status, for example {@code "draft"} or {@code "published"}. */
  @JsonProperty("status")
  private String status;

  /** Optimistic-concurrency revision counter. */
  @JsonProperty("revision_id")
  private Integer revisionId;

  /** Creation timestamp (ISO-8601). */
  @JsonProperty("created")
  private String created;

  /** Last-update timestamp (ISO-8601). */
  @JsonProperty("updated")
  private String updated;

  /** When the draft expires if never published. */
  @JsonProperty("expires_at")
  private String expiresAt;

  /** Access (visibility) settings of the record. */
  @JsonProperty("access")
  private B2instAccess access;

  /** PIDINST core metadata of the instrument. */
  @JsonProperty("metadata")
  private B2instInstrumentMetadata metadata;

  /** File container for the record's attachments. */
  @JsonProperty("files")
  private B2instRecordFiles files;

  /** File container for the record's media files. */
  @JsonProperty("media_files")
  private B2instRecordFiles mediaFiles;

  /** Parent record (versioning and ownership); loosely typed. */
  @JsonProperty("parent")
  private Map<String, Object> parent;

  /** Version index information; loosely typed. */
  @JsonProperty("versions")
  private Map<String, Object> versions;

  /** Persistent identifiers (DOI and so on); loosely typed. */
  @JsonProperty("pids")
  private Map<String, Object> pids;

  /** Usage statistics; loosely typed. */
  @JsonProperty("stats")
  private Map<String, Object> stats;

  /** Community custom fields; loosely typed. */
  @JsonProperty("custom_fields")
  private Map<String, Object> customFields;

  /** Soft-deletion status; loosely typed. */
  @JsonProperty("deletion_status")
  private Map<String, Object> deletionStatus;

  /** HATEOAS links for the draft record. */
  @JsonProperty("links")
  private B2instRecordLinks links;
}
