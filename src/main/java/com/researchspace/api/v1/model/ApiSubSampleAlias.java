package com.researchspace.api.v1.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ApiSubSampleAlias {

  private String alias;

  private String plural;

  public void setAlias(String alias) {
    this.alias = alias == null ? null : alias.trim();
  }

  public void setPlural(String plural) {
    this.plural = plural == null ? null : plural.trim();
  }
}
