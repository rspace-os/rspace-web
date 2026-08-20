package com.researchspace.b2inst.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HATEOAS links carried on a {@link B2instDraftRecord}.
 *
 * <p>Only the commonly used links are modelled; the server returns additional
 * links (archive, IIIF, access grants and so on) which are tolerated via
 * {@link JsonIgnoreProperties}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instRecordLinks {

  /** API self link (the draft endpoint while unpublished). */
  @JsonProperty("self")
  private String self;

  /** Human-facing HTML page for this record/draft. */
  @JsonProperty("self_html")
  private String selfHtml;

  /** Preview HTML page for the draft. */
  @JsonProperty("preview_html")
  private String previewHtml;

  /** Endpoint to reserve a DOI for the draft. */
  @JsonProperty("reserve_doi")
  private String reserveDoi;

  /** Draft files endpoint. */
  @JsonProperty("files")
  private String files;

  /** Draft media-files endpoint. */
  @JsonProperty("media_files")
  private String mediaFiles;

  /** Published record API link. */
  @JsonProperty("record")
  private String record;

  /** Published record HTML link. */
  @JsonProperty("record_html")
  private String recordHtml;

  /** Action link to publish the draft directly. */
  @JsonProperty("publish")
  private String publish;

  /** Action link to create or read the draft's review request. */
  @JsonProperty("review")
  private String review;

  /** Versions listing endpoint. */
  @JsonProperty("versions")
  private String versions;

  /** Access settings endpoint. */
  @JsonProperty("access")
  private String access;

  /** Communities listing endpoint for the record. */
  @JsonProperty("communities")
  private String communities;

  /** Community suggestions endpoint for the record. */
  @JsonProperty("communities-suggestions")
  private String communitiesSuggestions;

  /** Requests listing endpoint for the record. */
  @JsonProperty("requests")
  private String requests;
}
