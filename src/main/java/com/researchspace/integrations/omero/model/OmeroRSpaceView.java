package com.researchspace.integrations.omero.model;

import java.util.List;
import javax.json.JsonObject;

public interface OmeroRSpaceView {
  Long getId();

  int getChildCounts();

  String getName();

  Long getParentId();

  String getDescription();

  List<String> getAnnotations();

  String getOmeroConnectionKey();

  void setOmeroConnectionKey(String omeroConnectionKey);

  List<? extends OmeroRSpaceView> getChildren();

  String getType();

  default String nullSafeGetString(JsonObject jsonDataSet, String key) {
    return jsonDataSet.get(key) != null ? jsonDataSet.getString(key) : "";
  }

  default int nullSafeGetInt(JsonObject jsonDataSet, String key) {
    return jsonDataSet.get(key) != null ? jsonDataSet.getInt(key) : 0;
  }

  default String nullSafeGetNumberAsString(JsonObject jsonDataSet, String key) {
    return jsonDataSet.get(key) != null ? jsonDataSet.getJsonNumber(key).toString() : "";
  }

  default JsonObject nullSafeGetObject(JsonObject jsonDataSet, String key) {
    return jsonDataSet.get(key) != null ? jsonDataSet.getJsonObject(key) : null;
  }
}
