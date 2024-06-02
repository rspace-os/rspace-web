package com.researchspace.model.events;

import com.researchspace.service.archive.ImportArchiveReport;
import lombok.Value;

/** Event when import-from-XML is completed. */
@Value
public class ImportCompleted implements ProcessCompletedEvent<ImportArchiveReport> {

  private ImportArchiveReport report;
  private String importer;
}
