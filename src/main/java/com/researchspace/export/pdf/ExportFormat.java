package com.researchspace.export.pdf;

/** Supported export format */
public enum ExportFormat {
  PDF("pdf"),
  WORD("doc");

  private String suffix;

  private ExportFormat(String exportedFileSuffix) {
    this.suffix = exportedFileSuffix;
  }

  public String getSuffix() {
    return suffix;
  }
}
