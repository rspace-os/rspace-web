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
 * Response of {@code POST /api/records/{id}/draft/files}: the listing of
 * declared draft file entries.
 *
 * <p>Each entry starts in {@code pending} status until its bytes are uploaded
 * and committed.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instDraftFileList {

  /** Whether the draft accepts file attachments. */
  @JsonProperty("enabled")
  private Boolean enabled;

  /** Key of the file used for preview, or {@code null}. */
  @JsonProperty("default_preview")
  private String defaultPreview;

  /** Explicit display order of file keys. */
  @JsonProperty("order")
  private List<String> order;

  /** The declared file entries. */
  @JsonProperty("entries")
  private List<B2instDraftFile> entries;

  /** Listing-level links (for example {@code self}); kept as a simple map. */
  @JsonProperty("links")
  private Map<String, String> links;
}
