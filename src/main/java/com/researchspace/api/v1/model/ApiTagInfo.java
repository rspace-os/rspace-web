package com.researchspace.api.v1.model;

import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_NAME_DELIM;
import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_URL_DELIMITER;
import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_VERSION_DELIM;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Data
@EqualsAndHashCode
@NoArgsConstructor
public class ApiTagInfo {
  @JsonProperty("value")
  private String value;

  @JsonProperty("uri")
  private String uri;

  @JsonProperty("ontologyName")
  private String ontologyName;

  @JsonProperty("ontologyVersion")
  private String ontologyVersion;

  /**
   * Local tags have a value field and nothing else Tags from an ontology have ALL fields populated
   *
   * @return
   */
  @Override
  public String toString() {
    if (StringUtils.hasText(uri)) {
      return value
          + RSPACE_EXTONTOLOGY_URL_DELIMITER
          + uri
          + RSPACE_EXTONTOLOGY_NAME_DELIM
          + ontologyName
          + RSPACE_EXTONTOLOGY_VERSION_DELIM
          + ontologyVersion;
    } else {
      return value;
    }
  }
}
