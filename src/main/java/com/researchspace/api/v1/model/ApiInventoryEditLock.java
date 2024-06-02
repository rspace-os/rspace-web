package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({"globalId", "lockOwner"})
public class ApiInventoryEditLock {

  public static enum ApiInventoryEditLockStatus {
    LOCKED_OK,
    WAS_ALREADY_LOCKED,
    CANNOT_LOCK
  }

  @JsonProperty("globalId")
  private String globalId;

  @JsonProperty("lockOwner")
  private ApiUser owner;

  @JsonProperty("status")
  private ApiInventoryEditLockStatus status;

  @JsonProperty("remainingTimeInSeconds")
  private int remainingTimeInSeconds;

  @JsonProperty("message")
  private String message;
}
