/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Structure of an Advanced Search request */
@Data
@EqualsAndHashCode
@NoArgsConstructor
public class ApiSearchQuery {

  @JsonProperty("operator")
  private OperatorEnum operator = OperatorEnum.AND;

  @JsonProperty("terms")
  private List<ApiSearchTerm> terms = new ArrayList<ApiSearchTerm>();

  /** boolean operator */
  public enum OperatorEnum {
    @JsonProperty("and")
    AND("and"),

    @JsonProperty("or")
    OR("or");

    private String value;

    OperatorEnum(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  public ApiSearchQuery addTermsItem(ApiSearchTerm termsItem) {
    this.terms.add(termsItem);
    return this;
  }

  public boolean hasAnySearchTerms() {
    return terms != null && !terms.isEmpty();
  }
}
