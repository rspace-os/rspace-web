package com.researchspace.api.v1.controller;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class ApiFormSearchConfig extends ApiSearchConfig {

  public static final int MAX_QUERY_LENGTH = 2000;

  @Size(
      min = 2,
      max = MAX_QUERY_LENGTH,
      message = "Min query length is 2, max query length is " + MAX_QUERY_LENGTH + ".")
  // at least 2 continuous letter characters
  @Pattern(regexp = ".*\\w{2}.*")
  private String query;

  @Pattern(regexp = "(mine)|(all)")
  private String scope = "mine";

  @Override
  public MultiValueMap<String, String> toMap() {
    LinkedMultiValueMap<String, String> rc = new LinkedMultiValueMap<>();
    if (!StringUtils.isBlank(query)) {
      rc.add("query", query);
    }

    if (!StringUtils.isBlank(scope)) {
      rc.add("scope", scope);
    }
    return rc;
  }
}
