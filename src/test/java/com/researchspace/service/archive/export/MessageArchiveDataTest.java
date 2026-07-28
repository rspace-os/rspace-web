package com.researchspace.service.archive.export;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.testutils.SpringTransactionalTest;
import jakarta.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class MessageArchiveDataTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("messageArchiveHandler")
  private ArchiveDataHandler handler;

  @TempDir public File tempFolder;

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testHandleData() throws JAXBException, IOException {
    User exporter = createAndSaveUserIfNotExists(getRandomAlphabeticString("exp"));
    initialiseContentWithEmptyContent(exporter);
    ArchiveExportConfig aec = new ArchiveExportConfig();
    aec.setExportScope(ExportScope.SELECTION);
    aec.setArchiveType(ArchiveExportConfig.XML);
    aec.setExporter(exporter);
    handler.archiveData(aec, tempFolder);

    // check no messages.xml on selection based export.
    File messagesXML = new File(tempFolder, ExportImport.MESSAGES);
    assertFalse(messagesXML.exists());
    // but IS messages.xml o n message export
    aec.setExportScope(ExportScope.USER);
    aec.setUserOrGroupId(exporter.getOid());
    handler.archiveData(aec, tempFolder);

    messagesXML = new File(tempFolder, ExportImport.MESSAGES);
    assertTrue(messagesXML.exists());
  }
}
