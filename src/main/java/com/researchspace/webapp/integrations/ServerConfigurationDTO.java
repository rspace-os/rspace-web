package com.researchspace.webapp.integrations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.rometools.utils.Strings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode
public class ServerConfigurationDTO implements Comparable<ServerConfigurationDTO> {

  protected String alias;
  protected String url;

  @JsonInclude(value = Include.NON_EMPTY)
  protected String authUrl;

  public ServerConfigurationDTO(String alias, String apiUrl) {
    this.alias = alias;
    this.url = apiUrl;
  }

  @Override
  public int compareTo(@NotNull ServerConfigurationDTO o) {
    if (Strings.isBlank(this.getAlias())) {
      return this.getUrl().compareTo(o.getUrl());
    } else {
      return this.getAlias().compareTo(o.getAlias());
    }
  }
}
