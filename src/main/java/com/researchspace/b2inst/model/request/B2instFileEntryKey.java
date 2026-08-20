package com.researchspace.b2inst.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single declared file entry for {@code POST /api/records/{id}/draft/files}.
 *
 * <p>The request body is a JSON array of these objects, so the wire payload is a
 * {@code List<B2instFileEntryKey>}. To attach
 * several files, send one element per file, for example
 * {@code [{"key":"figure.png"},{"key":"article.pdf"}]}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instFileEntryKey {

  /** File name (key) the uploaded bytes will be stored under, for example {@code "file.png"}. */
  @JsonProperty("key")
  private String key;
}
