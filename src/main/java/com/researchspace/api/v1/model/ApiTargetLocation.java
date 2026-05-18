package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiTargetLocation implements Serializable {

  @JsonProperty("containerId")
  private Long containerId;

  @JsonProperty("location")
  private ApiContainerLocation containerLocation;
}
