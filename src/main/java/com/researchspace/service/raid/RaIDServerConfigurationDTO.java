package com.researchspace.service.raid;

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
public class RaIDServerConfigurationDTO extends ServerConfigurationDTO {

  @JsonInclude(value = Include.NON_EMPTY)
  private Integer servicePointId;

  @JsonInclude(value = Include.NON_EMPTY)
  private String clientId;

  @JsonInclude(value = Include.NON_EMPTY)
  private String clientSecret;

  public RaIDServerConfigurationDTO(String alias, String apiUrl, String authUrl) {
    super(alias, apiUrl, authUrl);
  }
}
