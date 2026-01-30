package com.researchspace.webapp.integrations.dsw.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class DSWUser {

  @JsonProperty private String active;
  @JsonProperty private String affiliation;
  @JsonProperty private String createdAt;
  @JsonProperty private String email;
  @JsonProperty private String firstName;
  @JsonProperty private String imageUrl;
  @JsonProperty private String lastName;
  @JsonProperty private String lastSeenNewsId;
  @JsonProperty private String locale;
  @JsonProperty private String[] permissions;
  @JsonProperty private String role;
  @JsonProperty private String[] sources;
  @JsonProperty private String updatedAt;
  @JsonProperty private String uuid;
}
