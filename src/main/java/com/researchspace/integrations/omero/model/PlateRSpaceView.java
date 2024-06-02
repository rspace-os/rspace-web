package com.researchspace.integrations.omero.model;

import com.researchspace.webapp.integrations.omero.JSONClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.json.JsonObject;
import lombok.Getter;

@Getter
public class PlateRSpaceView implements OmeroRSpaceView {
  private static final String TYPE = "plate";
  private final Long id;
  private final Long parentId;
  private int childCounts = 0;
  private final String name;
  private final String description;
  private List<String> annotations = new ArrayList<>();
  private String omeroConnectionKey;

  public PlateRSpaceView(JsonObject jsonPlate, JSONClient jsonClient, Long parentID) {
    this.name = nullSafeGetString(jsonPlate, "name");
    this.description = nullSafeGetString(jsonPlate, "Description");
    this.id = Long.valueOf(jsonPlate.getInt("id"));
    this.parentId = parentID;
    this.childCounts = nullSafeGetInt(jsonPlate, "childCount");
  }

  @Override
  public void setOmeroConnectionKey(String omeroConnectionKey) {
    this.omeroConnectionKey = omeroConnectionKey;
  }

  @Override
  public List<? extends OmeroRSpaceView> getChildren() {
    return Collections.EMPTY_LIST;
  }

  @Override
  public String getType() {
    return TYPE;
  }
}
