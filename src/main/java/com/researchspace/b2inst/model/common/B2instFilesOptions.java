package com.researchspace.b2inst.model.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The {@code files} option block sent when creating a draft record,
 * declaring whether the record will carry file attachments.
 *
 * <p>This is distinct from the richer {@code files} object returned on a record
 * (see {@code com.researchspace.b2inst.model.response.B2instRecordFiles}), which also
 * lists entries and byte totals.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instFilesOptions {

  /** Whether the record accepts file attachments. */
  @JsonProperty("enabled")
  private Boolean enabled;
}
