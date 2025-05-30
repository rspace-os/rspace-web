package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import lombok.Data;

/** Represents the JSON response returned when refreshing / adding a new OAuth token */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class NewOAuthTokenResponse {
  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("refresh_token")
  private String refreshToken;

  @JsonIgnore private Instant expiryTime;

  private String scope = "all";

  @JsonProperty("token_type")
  private static String TOKEN_TYPE = "bearer";

  @JsonProperty("expires_in")
  public Long expiresIn() {
    return Duration.between(Instant.now(), expiryTime).getSeconds();
  }
}
