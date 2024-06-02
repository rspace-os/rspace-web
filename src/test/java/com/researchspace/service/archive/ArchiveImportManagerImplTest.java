package com.researchspace.service.archive;

import static com.researchspace.core.util.progress.ProgressMonitor.NULL_MONITOR;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveModel;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ArchiveImportManagerImplTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock ArchiveModelToDatabaseSaver saver;
  @Mock IArchiveParser parser;
  ArchiveImporterManagerImpl archiveImporter;

  @Before
  public void setUp() throws Exception {
    archiveImporter = new ArchiveImporterManagerImpl();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testImportArchiveDoesNotProceedIfValidationFails() {
    final ImportArchiveReport report = new ImportArchiveReport();
    final ArchivalImportConfig cfg = new ArchivalImportConfig();
    final ArchiveModel model = new ArchiveModel();
    archiveImporter.setDatabaseSaver(saver);
    when(parser.loadArchive(
            Mockito.any(File.class),
            Mockito.any(ImportArchiveReport.class),
            Mockito.any(ArchivalImportConfig.class)))
        .thenReturn(model);

    archiveImporter.importArchive(new File("any"), cfg, NULL_MONITOR, null);
    verify(saver, never()).saveArchiveToDB(cfg, report, model, NULL_MONITOR, null);
  }
}
