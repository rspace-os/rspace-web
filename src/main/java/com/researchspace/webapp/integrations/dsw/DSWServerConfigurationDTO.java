package com.researchspace.webapp.integrations.dsw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.researchspace.webapp.integrations.ServerConfigurationDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = true)
public class DSWServerConfigurationDTO extends ServerConfigurationDTO {

  @JsonInclude(value = Include.NON_EMPTY)
  private String token;

  public DSWServerConfigurationDTO(String alias, String apiUrl) {
    super(alias, apiUrl);
  }
}
