package com.researchspace.webapp.integrations.dsw.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class DSWProjectFromUUID {
  @JsonProperty private long fileCount;
  @JsonProperty private boolean isTemplate;
  @JsonProperty private String knowledgeModelPackageId;
  @JsonProperty private String migrationUuid;
  @JsonProperty private String name;
  @JsonProperty private DSWProjectPermission[] permissions;
  @JsonProperty private String sharing;
  @JsonProperty private String uuid;
  @JsonProperty private String visibility;

  @Getter
  private class DSWProjectPermission {

    @JsonProperty private DSWProjectMember member;
    @JsonProperty private String[] perms;
    @JsonProperty private String projectUuid;

    @Getter
    private class DSWProjectMember {
      @JsonProperty private String firstName;
      @JsonProperty private String gravatarHash;
      @JsonProperty private String imageUrl;
      @JsonProperty private String lastName;
      @JsonProperty private String type;
      @JsonProperty private String uuid;
    }
  }
}
