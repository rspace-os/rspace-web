package com.researchspace.webapp.integrations.pyrat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.researchspace.webapp.integrations.ServerConfigurationDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = true)
public class PyratServerConfigurationDTO extends ServerConfigurationDTO {

  @JsonInclude(value = Include.NON_EMPTY)
  // RSDEV-1525: read from deployment.properties, never serialized back to the browser
  @JsonProperty(access = Access.WRITE_ONLY)
  private String token;

  public PyratServerConfigurationDTO(String alias, String apiUrl) {
    super(alias, apiUrl);
  }
}
