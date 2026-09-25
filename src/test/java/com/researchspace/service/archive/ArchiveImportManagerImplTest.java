package com.researchspace.service.archive;

import static com.researchspace.core.util.progress.ProgressMonitor.NULL_MONITOR;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveModel;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ArchiveImportManagerImplTest {
  @Mock ArchiveModelToDatabaseSaver saver;
  @Mock IArchiveParser parser;
  ArchiveImporterManagerImpl archiveImporter;

  @BeforeEach
  public void setUp() throws Exception {
    archiveImporter = new ArchiveImporterManagerImpl();
  }

  @Test
  public void testImportArchiveDoesNotProceedIfValidationFails() {
    final ImportArchiveReport report = new ImportArchiveReport();
    final ArchivalImportConfig cfg = new ArchivalImportConfig();
    final ArchiveModel model = new ArchiveModel();
    archiveImporter.setDatabaseSaver(saver);

    archiveImporter.importArchive(new File("any"), cfg, NULL_MONITOR, null);
    verify(saver, never()).saveArchiveToDB(cfg, report, model, NULL_MONITOR, null);
  }
}
