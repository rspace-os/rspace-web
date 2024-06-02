package com.researchspace.service.impl;

import com.researchspace.model.core.RecordType;
import com.researchspace.service.archive.IExportUtils;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

/** Utility helper methods for exporting/archiving */
class AbstractExporter {

  @Setter @Autowired protected IExportUtils exportUtils;

  void verifyInput(Long[] exportIds, String[] exportTypes) {
    int x1 = exportIds.length, x3 = exportTypes.length;
    int dx2 = x1 - x3;
    if (dx2 > 0) {
      for (int i = x3; i < x1; i++) {
        exportTypes[i] = RecordType.NORMAL.name();
      }
    }
  }
}
