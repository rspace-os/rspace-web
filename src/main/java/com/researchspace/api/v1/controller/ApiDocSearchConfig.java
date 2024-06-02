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
public class ApiDocSearchConfig extends ApiSearchConfig {

  public static final int MAX_QUERY_LENGTH = 2000;

  @Size(max = MAX_QUERY_LENGTH, message = "Max query length is " + MAX_QUERY_LENGTH + ".")
  private String query;

  @Size(max = MAX_QUERY_LENGTH, message = "Max advanced query length is " + MAX_QUERY_LENGTH + ".")
  private String advancedQuery;

  @Pattern(regexp = "(favorites)|(sharedWithMe)")
  private String filter;

  @Override
  public MultiValueMap<String, String> toMap() {
    LinkedMultiValueMap<String, String> rc = new LinkedMultiValueMap<>();
    if (!StringUtils.isBlank(query)) {
      rc.add("query", query);
    }
    if (!StringUtils.isBlank(advancedQuery)) {
      rc.add("advancedQuery", advancedQuery);
    }
    if (!StringUtils.isBlank(filter)) {
      rc.add("filter", filter);
    }

    return rc;
  }
}
