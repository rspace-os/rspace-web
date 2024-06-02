package com.researchspace.slack;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AttachmentField {
  private String title, value;

  @JsonProperty("short")
  private boolean isShort = true;

  ;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public boolean isShort() {
    return isShort;
  }

  public void setShort(boolean isShort) {
    this.isShort = isShort;
  }

  public AttachmentField() {}

  public AttachmentField(String title, String value) {
    super();
    this.title = title;
    this.value = value;
  }
}
