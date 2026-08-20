package com.researchspace.b2inst.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.b2inst.model.common.B2instAccess;
import com.researchspace.b2inst.model.common.B2instFilesOptions;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/records}, which creates the draft
 * instrument record.
 *
 * <p>Carries the access settings, the PIDINST {@link B2instInstrumentMetadata} and the
 * file-handling option. The response is modelled by
 * {@code com.researchspace.b2inst.model.response.B2instDraftRecord}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instDoi {

  /** Visibility settings for the record and its files. */
  @JsonProperty("access")
  private B2instAccess access;

  /** PIDINST core metadata describing the instrument. */
  @JsonProperty("metadata")
  private B2instInstrumentMetadata metadata;

  /** Declares whether the record will carry file attachments. */
  @JsonProperty("files")
  private B2instFilesOptions files;
}
