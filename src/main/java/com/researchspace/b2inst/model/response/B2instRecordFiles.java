package com.researchspace.b2inst.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The {@code files} (and {@code media_files}) container carried on a
 * {@link B2instDraftRecord}.
 *
 * <p>Unlike the draft file-listing returned by the upload endpoints (see
 * {@link B2instDraftFileList}), the record-level container keys its {@link #entries} by
 * file name and reports aggregate counts. On a freshly created draft it is enabled but empty.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instRecordFiles {

  /** Whether the record accepts file attachments. */
  @JsonProperty("enabled")
  private Boolean enabled;

  /** Key of the file used for preview, or {@code null}. */
  @JsonProperty("default_preview")
  private String defaultPreview;

  /** Explicit display order of file keys. */
  @JsonProperty("order")
  private List<String> order;

  /** Number of committed files. */
  @JsonProperty("count")
  private Integer count;

  /** Total size of all committed files in bytes. */
  @JsonProperty("total_bytes")
  private Long totalBytes;

  /** Committed file entries, keyed by file name. */
  @JsonProperty("entries")
  private Map<String, B2instDraftFile> entries;
}
