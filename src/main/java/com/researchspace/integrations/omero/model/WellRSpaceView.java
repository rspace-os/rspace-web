package com.researchspace.integrations.omero.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.json.JsonArray;
import javax.json.JsonObject;
import lombok.Getter;
import lombok.SneakyThrows;

@Getter
public class WellRSpaceView implements OmeroRSpaceView {
  public static List<JsonObject> getWellSamples(JsonObject jsonWell) {
    List<JsonObject> wellSamples = new ArrayList<>();
    JsonArray wellSamplesArray = jsonWell.getJsonArray("WellSamples");
    for (int i = 0; i < wellSamplesArray.size(); i++) {
      JsonObject jws = wellSamplesArray.getJsonObject(i);
      wellSamples.add(jws);
    }
    return wellSamples;
  }

  private static final String TYPE = "well";
  private final String name;
  private final Long id;
  private final String description;
  private final Long parentId;
  private String omeroConnectionKey;
  private List<String> annotations;
  private List<WellSampleDataRSpaceView> wellSampleDataRSpaceViews = new ArrayList<>();
  private final String column;
  private final String row;

  @SneakyThrows // not used due to performance issues
  public WellRSpaceView(
      JsonObject jsonWell,
      Long parentID,
      Map<Long, String> imageThumbnails,
      List<String> annotations,
      Map<Long, JsonObject> fullImages,
      Map<Long, List<String>> imagesAnnotationsMap) {
    this.name = nullSafeGetString(jsonWell, "Name");
    this.description = nullSafeGetString(jsonWell, "Description");
    this.id = Long.valueOf(jsonWell.getInt("@id"));
    this.parentId = parentID;
    this.annotations = annotations;
    this.column = nullSafeGetNumberAsString(jsonWell, "Column");
    this.row = nullSafeGetNumberAsString(jsonWell, "Row");
    for (JsonObject wellSample : getWellSamples(jsonWell)) {
      wellSampleDataRSpaceViews.add(
          new WellSampleDataRSpaceView(
              wellSample, imageThumbnails, fullImages, imagesAnnotationsMap, parentID));
    }
  }

  @SneakyThrows
  public WellRSpaceView(
      JsonObject jsonWell,
      Long parentID,
      Map<Long, String> imageThumbnails,
      List<String> annotations) {
    this.name = nullSafeGetString(jsonWell, "Name");
    this.description = nullSafeGetString(jsonWell, "Description");
    this.id = Long.valueOf(jsonWell.getInt("@id"));
    this.parentId = parentID;
    this.annotations = annotations;
    this.column = nullSafeGetNumberAsString(jsonWell, "Column");
    this.row = nullSafeGetNumberAsString(jsonWell, "Row");
    for (JsonObject wellSample : getWellSamples(jsonWell)) {
      wellSampleDataRSpaceViews.add(
          new WellSampleDataRSpaceView(wellSample, imageThumbnails, parentID));
    }
  }

  @Override
  public int getChildCounts() {
    return this.wellSampleDataRSpaceViews.size();
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
    return wellSampleDataRSpaceViews;
  }

  @Override
  public String getType() {
    return TYPE;
  }
}
