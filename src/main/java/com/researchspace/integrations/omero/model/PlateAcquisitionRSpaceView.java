package com.researchspace.integrations.omero.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.json.JsonArray;
import javax.json.JsonObject;
import lombok.Getter;
import org.springframework.util.StringUtils;

@Getter
public class PlateAcquisitionRSpaceView implements OmeroRSpaceView {
  private static final String TYPE = "plateAcquisition";
  private final Long id;
  private final Long parentId;
  private final int columns;
  private final int rows;
  private final boolean fake;
  private int childCounts = 0;
  private final String name;
  private final String description;
  private List<String> annotations = new ArrayList<>();
  private String omeroConnectionKey;
  private List<String> samplesUrls = new ArrayList<>();

  public PlateAcquisitionRSpaceView(
      JsonObject jsonPlateAcquisition, JsonObject jsonPlate, Long parentID) {
    String name = nullSafeGetString(jsonPlateAcquisition, "Name");
    this.description = nullSafeGetString(jsonPlateAcquisition, "Description");
    this.id = Long.valueOf(jsonPlateAcquisition.getInt("@id"));
    if (!StringUtils.hasText(name)) {
      this.name = "Run " + this.getId();
    } else {
      this.name = name;
    }
    this.parentId = parentID;
    this.childCounts = nullSafeGetInt(jsonPlate, "Columns") * nullSafeGetInt(jsonPlate, "Rows");
    this.columns = nullSafeGetInt(jsonPlate, "Columns");
    this.rows = nullSafeGetInt(jsonPlate, "Rows");
    this.fake = false;
    JsonArray samplesUrlsArray = jsonPlateAcquisition.getJsonArray("url:wellsampleindex_wells");
    for (int i = 0; i < samplesUrlsArray.size(); i++) {
      this.getSamplesUrls().add(samplesUrlsArray.getString(i));
    }
  }

  public PlateAcquisitionRSpaceView(JsonObject jsonPlate, long parentID) {
    this.name = "Acquisition for " + nullSafeGetString(jsonPlate, "Name");
    this.description = "";
    this.id = parentID;
    this.parentId = parentID;
    this.childCounts = nullSafeGetInt(jsonPlate, "Columns") * nullSafeGetInt(jsonPlate, "Rows");
    this.columns = nullSafeGetInt(jsonPlate, "Columns");
    this.rows = nullSafeGetInt(jsonPlate, "Rows");
    this.fake = true;
    JsonArray samplesUrlsArray = jsonPlate.getJsonArray("url:wellsampleindex_wells");
    for (int i = 0; i < samplesUrlsArray.size(); i++) {
      this.getSamplesUrls().add(samplesUrlsArray.getString(i));
    }
  }

  @Override
  public void setOmeroConnectionKey(String omeroConnectionKey) {
    this.omeroConnectionKey = omeroConnectionKey;
  }

  @Override
  public List<OmeroRSpaceView> getChildren() {
    return Collections.EMPTY_LIST;
  }

  public boolean isFake() {
    return fake;
  }

  @Override
  public String getType() {
    return TYPE;
  }
}
