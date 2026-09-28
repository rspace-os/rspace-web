package com.researchspace.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One entry from BioPortal's {@code GET /search} REST API response. Only the fields RSpace's
 * ontology-tag mapping needs are modelled, unrelated fields are ignored.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BioPortalSearchResult {

  @JsonProperty("@id")
  private String id;

  @JsonProperty private String prefLabel;

  @JsonProperty private BioPortalLinks links;
}
