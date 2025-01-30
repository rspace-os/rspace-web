package com.researchspace.webapp.integrations.pyrat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.rometools.utils.Strings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PyratServerDTO implements Comparable<PyratServerDTO> {

  private String alias;
  private String url;

  @JsonInclude(value = Include.NON_EMPTY)
  private String token;

  public PyratServerDTO(String alias, String url) {
    this.alias = alias;
    this.url = url;
  }

  @Override
  public int compareTo(@NotNull PyratServerDTO o) {
    if (Strings.isBlank(this.getAlias())) {
      return this.getUrl().compareTo(o.getUrl());
    } else {
      return this.getAlias().compareTo(o.getAlias());
    }
  }
}
