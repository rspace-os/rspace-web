package com.researchspace.service.archive.export;

import com.researchspace.archive.model.IArchiveExportConfig;
import java.io.File;
import java.io.IOException;
import javax.xml.bind.JAXBException;

/** Defines a handler method for exporting a subset of RSpace data to XML */
public interface ArchiveDataHandler {
  /**
   * Handler method for exporting a subset of RSpace data
   *
   * @param aconfig An {@link IArchiveExportConfig}
   * @param archiveAssmblyFlder A {@link File} that is a folder in which the archive can be
   *     assembled
   * @throws JAXBException
   * @throws IOException
   */
  void archiveData(IArchiveExportConfig aconfig, File archiveAssmblyFlder)
      throws JAXBException, IOException;
}
