package com.researchspace.b2inst.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Identifier of a resource related to the instrument
 * (PIDINST {@code RelatedIdentifier}), such as a manual, dataset or publication.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instRelatedIdentifier {

  /** Type of related identifier, for example {@code "URL"} or {@code "DOI"}. */
  @JsonProperty("relatedIdentifierType")
  private String relatedIdentifierType;

  /** The related identifier value. */
  @JsonProperty("relatedIdentifierValue")
  private String relatedIdentifierValue;

  /**
   * Nature of the relation between the instrument and the related resource,
   * for example {@code "IsDescribedBy"}.
   */
  @JsonProperty("relationType")
  private String relationType;

  /** Optional human-readable name of the related resource. */
  @JsonProperty("relatedIdentifierName")
  private String relatedIdentifierName;
}
