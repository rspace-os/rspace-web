package com.researchspace.service.archive.export;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.IOException;
import javax.xml.bind.JAXBException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class MessageArchiveDataTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("messageArchiveHandler")
  private ArchiveDataHandler handler;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {}

  @After
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
    handler.archiveData(aec, tempFolder.getRoot());

    // check no messages.xml on selection based export.
    File messagesXML = new File(tempFolder.getRoot(), ExportImport.MESSAGES);
    assertFalse(messagesXML.exists());
    // but IS messages.xml o n message export
    aec.setExportScope(ExportScope.USER);
    aec.setUserOrGroupId(exporter.getOid());
    handler.archiveData(aec, tempFolder.getRoot());

    messagesXML = new File(tempFolder.getRoot(), ExportImport.MESSAGES);
    assertTrue(messagesXML.exists());
  }
}
