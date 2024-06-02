package com.researchspace.api.v1.controller;

import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Generic search config for simple search API with single 'query' term. */
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class ApiGenericSearchConfig extends ApiSearchConfig {

  public static final int MAX_QUERY_LENGTH = 2000;

  @Size(max = MAX_QUERY_LENGTH, message = "Max query length is " + MAX_QUERY_LENGTH + ".")
  private String query;

  @Override
  public MultiValueMap<String, String> toMap() {
    LinkedMultiValueMap<String, String> rc = new LinkedMultiValueMap<>();
    if (!StringUtils.isBlank(query)) {
      rc.add("query", query);
    }
    return rc;
  }
}
