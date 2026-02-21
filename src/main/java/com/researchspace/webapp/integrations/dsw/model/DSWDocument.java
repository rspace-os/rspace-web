package com.researchspace.webapp.integrations.dsw.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties({"submissions"})
public class DSWDocument {
  private String createdAt;
  private String createdBy;
  private String documentTemplateId;
  private String documentTemplateName;
  private Long fileSize;
  private Format format;
  private String name;
  private Project project;
  private String projectEventUuid;
  private String projectVersion;
  private String state;
  private String uuid;
  private String workerLog;

  @Getter
  public class Format {
    private String icon;
    private String name;
    private String uuid;
  }

  @Getter
  public class Project {
    private String name;
    private String uuid;
  }
}
