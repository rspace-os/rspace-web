package com.researchspace.integrations.omero.model;

import com.researchspace.webapp.integrations.omero.JSONClient;
import java.util.ArrayList;
import java.util.List;
import javax.json.JsonObject;
import lombok.Getter;

@Getter
public class ScreenRSpaceView implements OmeroRSpaceView {
  private static final String TYPE = "screen";
  private final String name;
  private final Long id;
  private final String description;
  private final int childCounts;
  private String omeroConnectionKey;
  private final List<PlateRSpaceView> plates = new ArrayList<>();
  private List<String> annotations = new ArrayList<>();

  public ScreenRSpaceView(JsonObject jsonScreen, JSONClient jsonClient) {
    this.name = nullSafeGetString(jsonScreen, "Name");
    this.description = nullSafeGetString(jsonScreen, "Description");
    this.id = Long.valueOf(jsonScreen.getInt("@id"));
    this.childCounts = nullSafeGetInt(jsonScreen, "omero:childCount");
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
    return this.plates;
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
