package com.researchspace.webapp.integrations.dsw.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties({"page"})
public class DSWProjects {
  // @JsonProperty private DSWProject[] projects;
  @JsonProperty private ProjectsArr _embedded;

  // @JsonProperty private String page; // Not needed, so ignore?

  public DSWProject[] getProjects() {
    return this._embedded.projects;
  }

  @Getter
  public class ProjectsArr {
    @JsonProperty private DSWProject[] projects;

    public ProjectsArr() {}
  }
}
