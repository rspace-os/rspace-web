package com.researchspace.webapp.integrations.dsw.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties({"page"})
public class DSWProjects {
  @JsonProperty private ProjectsArr _embedded;

  public DSWProject[] getProjects() {
    return this._embedded.projects;
  }

  @Getter
  public class ProjectsArr {
    @JsonProperty private DSWProject[] projects;

    public ProjectsArr() {}
  }
}
