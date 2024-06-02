package com.researchspace.integrations.omero.model;

import com.researchspace.webapp.integrations.omero.JSONClient;
import java.util.ArrayList;
import java.util.List;
import javax.json.JsonObject;
import lombok.Getter;

@Getter
public class ProjectRSpaceView implements OmeroRSpaceView {
  public static final String TYPE = "project";
  private final String name;
  private final Long id;
  private final String description;
  private final int childCounts;
  private String omeroConnectionKey;
  private final List<DataSetRSpaceView> datasets = new ArrayList<>();
  private List<String> annotations = new ArrayList<>();

  public ProjectRSpaceView(JsonObject jsonProject, JSONClient jsonClient) {
    this.name = nullSafeGetString(jsonProject, "Name");
    this.description = nullSafeGetString(jsonProject, "Description");
    this.id = Long.valueOf(jsonProject.getInt("@id"));
    this.childCounts = nullSafeGetInt(jsonProject, "omero:childCount");
  }

  @Override
  public void setOmeroConnectionKey(String omeroConnectionKey) {
    this.omeroConnectionKey = omeroConnectionKey;
    for (OmeroRSpaceView child : getChildren()) {
      child.setOmeroConnectionKey(omeroConnectionKey);
    }
  }

  @Override
  public List<? extends OmeroRSpaceView> getChildren() {
    return this.datasets;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public Long getParentId() {
    return null;
  }
}
