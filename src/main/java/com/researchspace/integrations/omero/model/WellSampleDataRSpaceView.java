package com.researchspace.integrations.omero.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.json.JsonObject;
import lombok.Getter;

@Getter
public class WellSampleDataRSpaceView implements OmeroRSpaceView {
  private static final String TYPE = "well sample";
  private final String name;
  private final Long id;
  private final String description;
  private final Long parentId;
  private final String plateAcquisitionName;
  private String omeroConnectionKey;
  private final List<String> annotations = new ArrayList<>();
  private final ImageRSpaceView imageRSpaceView;

  public WellSampleDataRSpaceView(
      JsonObject jsonWellSample,
      Map<Long, String> imageThumbnails,
      Map<Long, JsonObject> fullImages,
      Map<Long, List<String>> imagesAnnotationsMap,
      Long parentID) {
    this.name = nullSafeGetString(jsonWellSample, "Name");
    this.description = nullSafeGetString(jsonWellSample, "Description");
    JsonObject plateAcquisition = nullSafeGetObject(jsonWellSample, "PlateAcquisition");
    this.plateAcquisitionName =
        plateAcquisition != null ? nullSafeGetString(plateAcquisition, "Name") : "";
    this.id = Long.valueOf(jsonWellSample.getInt("@id"));
    this.parentId = parentID;
    Long imageID = Long.valueOf(jsonWellSample.getJsonObject("Image").getInt("@id"));
    JsonObject imageJson = fullImages.get(imageID);
    List<String> imageAnnotations =
        imagesAnnotationsMap.get(imageID) != null
            ? imagesAnnotationsMap.get(imageID)
            : Collections.emptyList();
    imageRSpaceView =
        new ImageRSpaceView(
            imageJson,
            parentID,
            imageThumbnails.get(imageID),
            imageAnnotations,
            plateAcquisitionName);
  }

  public WellSampleDataRSpaceView(
      JsonObject jsonWellSample, Map<Long, String> imageThumbnails, Long parentID) {
    this.name = nullSafeGetString(jsonWellSample, "Name");
    this.description = nullSafeGetString(jsonWellSample, "Description");
    this.id = Long.valueOf(jsonWellSample.getInt("@id"));
    this.parentId = parentID;
    JsonObject plateAcquisition = nullSafeGetObject(jsonWellSample, "PlateAcquisition");
    this.plateAcquisitionName =
        plateAcquisition != null ? nullSafeGetString(plateAcquisition, "Name") : "";
    Long imageID = Long.valueOf(jsonWellSample.getJsonObject("Image").getInt("@id"));
    imageRSpaceView =
        new ImageRSpaceView(
            jsonWellSample.getJsonObject("Image"),
            parentID,
            imageThumbnails.get(imageID),
            plateAcquisitionName);
  }

  @Override
  public int getChildCounts() {
    return 1;
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
    return List.of(imageRSpaceView);
  }

  @Override
  public String getType() {
    return TYPE;
  }
}
