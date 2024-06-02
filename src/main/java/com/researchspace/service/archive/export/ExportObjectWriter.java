package com.researchspace.service.archive.export;

import java.io.File;

public interface ExportObjectWriter {

  void writeExportObject(File outputFile, ExportedRecord exported);
}
