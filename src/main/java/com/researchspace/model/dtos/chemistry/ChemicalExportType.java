package com.researchspace.model.dtos.chemistry;

public enum ChemicalExportType {
  PNG("png"),
  JPEG("jpeg"),
  SVG("svg");

  private final String type;

  ChemicalExportType(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}
