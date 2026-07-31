package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.service.archive.IExportUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

public class PDFUtilsTest extends SpringTransactionalTest {

  private @Autowired IExportUtils pdfUtils;
  final File pdffile = new File("src/test/resources/TestResources/smartscotland3.pdf");
  User anyUser = null;
  EcatDocumentFile emf = null;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    emf = addToGallery(pdffile, anyUser);
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testPDFThumbnailGeneration() throws Exception {
    //		FileStore fs = new DummyFileStore();
    //		User u= TestFactory.createAnyUser("any");
    //		FileProperty fp = new FileProperty();
    //		fp.setFileOwner(u.getUsername());
    //		fp.setFileUser(u.getUsername());
    //		fp.setRoot(new FileStoreRoot("any"));
    //		fs.save(fp, pdffile, FileDuplicateStrategy.REPLACE);
    //		pdfUtils.setFileStore(fs);

    ConversionResult result = pdfUtils.createThumbnailForExport(emf.getFileName(), anyUser);
    assertTrue(result.isSuccessful());
    assertTrue(FileUtils.readFileToByteArray(result.getConverted()).length > 0); // some content.
  }

  @Test
  public void testPDFDisplay() throws Exception {
    //		FileStore fs = new DummyFileStore();
    //		User u = TestFactory.createAnyUser("any");
    //		FileProperty fp = new FileProperty();
    //		fp.setFileOwner(u.getUsername());
    //		fp.setFileUser(u.getUsername());
    //		URI saved = fs.save(fp, pdffile, FileDuplicateStrategy.REPLACE);
    HttpServletResponse resp = new MockHttpServletResponse();
    // pdfUtils.setFileStore(fs);
    pdfUtils.display(emf.getFileName(), anyUser, resp);
    assertEquals("application/pdf", resp.getContentType());
  }
}
