package com.researchspace.b2inst.model.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Access settings of a record.
 *
 * <p>On a create-record request only {@link #record} and {@link #files} are sent; the
 * response additionally carries {@link #embargo} and a derived
 * {@link #status}. Both directions share this single type, with the
 * response-only fields left {@code null} when building a request.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instAccess {

  /** Visibility of the record metadata, for example {@code "public"} or {@code "restricted"}. */
  @JsonProperty("record")
  private String record;

  /** Visibility of the record files, for example {@code "public"} or {@code "restricted"}. */
  @JsonProperty("files")
  private String files;

  /** Embargo settings (response only). */
  @JsonProperty("embargo")
  private B2instEmbargo embargo;

  /**
   * Server-derived access status (response only), for example
   * {@code "metadata-only"}, {@code "open"} or {@code "embargoed"}.
   */
  @JsonProperty("status")
  private String status;
}
