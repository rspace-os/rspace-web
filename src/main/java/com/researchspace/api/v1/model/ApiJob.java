package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ApiJob extends LinkableApiObject {

  private Long id;
  private String status;
  private double percentComplete = -1;

  private Object result;

  /** Boolean tag indicating is completed successfully. */
  @JsonIgnore private boolean completed;

  /** Can be null if resource not yet created. */
  @JsonIgnore private String resourceLocation;

  public ApiJob(Long id, String status) {
    this.id = id;
    this.status = status;
  }
}
