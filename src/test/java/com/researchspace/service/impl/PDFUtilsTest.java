package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.service.archive.IExportUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

public class PDFUtilsTest extends SpringTransactionalTest {

  private @Autowired IExportUtils pdfUtils;
  final File pdffile = new File("src/test/resources/TestResources/smartscotland3.pdf");
  User anyUser = null;
  EcatDocumentFile emf = null;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    emf = addToGallery(pdffile, anyUser);
  }

  @After
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
