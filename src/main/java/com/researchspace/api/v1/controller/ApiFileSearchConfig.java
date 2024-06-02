package com.researchspace.api.v1.controller;

import javax.validation.constraints.Pattern;
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
public class ApiFileSearchConfig extends ApiSearchConfig {

  @Pattern(regexp = "(image)|(document)|(av)||(chemistry)")
  private String mediaType;

  @Override
  public MultiValueMap<String, String> toMap() {
    LinkedMultiValueMap<String, String> rc = new LinkedMultiValueMap<>();
    if (!StringUtils.isBlank(mediaType)) {
      rc.add("mediaType", mediaType);
    }
    return rc;
  }
}
