package com.researchspace.export.pdf;

import java.io.File;
import lombok.Value;

@Value
public class ExportOperationDetails {

  private File tempExportFolder;
  private File concatenatedExportFile;
}
