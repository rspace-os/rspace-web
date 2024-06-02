package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/** It is an API representation of an external filestore lacotion */
@Data
@Builder
@AllArgsConstructor
@ToString(callSuper = true)
@JsonPropertyOrder(value = {"id", "name", "path", "_links"})
public class ApiConfiguredLocation extends LinkableApiObject {

  @JsonProperty("id")
  private Long id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("path")
  private String path;

  public ApiConfiguredLocation() {}
}
