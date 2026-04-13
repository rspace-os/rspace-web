package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.core.testutil.JavaxValidatorTest;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.IMediaFactory;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.ResponseEntity;

public class ImageControllerTest extends JavaxValidatorTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock UserManager usrMgr;
  @Mock RecordManager recordMgr;
  @Mock BaseRecordManager baseRecordManager;
  @Mock IMediaFactory mediaFactory;
  @Mock DocumentConversionService docConverter;
  @InjectMocks private ImageController imgController;
  User anyUser;

  @Before
  public void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
    StaticMessageSource msg = new StaticMessageSource();
    msg.addMessage("record.inaccessible", Locale.getDefault(), "auth");
    imgController.setMessageSource(new MessageSourceUtils(msg));
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void generateThumbnailNotCalledAfterMaxRetriesExceeeded() throws IOException {
    EcatDocumentFile doc = setUpThumbnailMocks();
    doc.setNumThumbnailConversionAttemptsMade((byte) 3);
    byte[] fallback = new byte[] {1, 2, 3, 4};
    when(mediaFactory.getFileSuffixIcon(doc.getFileName())).thenReturn(fallback);
    ResponseEntity<byte[]> resp = imgController.getDocThumbnail(doc.getId(), null);
    assertEquals(fallback.length, resp.getBody().length);
    verify(recordMgr, never()).save(doc, anyUser);
  }

  private EcatDocumentFile setUpThumbnailMocks() {
    EcatDocumentFile doc = TestFactory.createEcatDocument(123, anyUser);
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
    when(baseRecordManager.retrieveMediaFile(
            Mockito.eq(anyUser),
            Mockito.eq(doc.getId()),
            Mockito.any(),
            Mockito.any(),
            Mockito.any()))
        .thenReturn(doc);
    return doc;
  }

  @Test
  public void generateThumbnailCalledIfNotExists() throws IOException {
    EcatDocumentFile doc = setUpThumbnailMocks();
    when(docConverter.supportsConversion(doc, "png")).thenReturn(true);
    doc.setNumThumbnailConversionAttemptsMade((byte) 1);
    ConversionResult result = new ConversionResult(RSpaceTestUtils.getAnyAttachment(), "png");
    when(docConverter.convert(Mockito.eq(doc), Mockito.eq("png"), Mockito.any(File.class)))
        .thenReturn(result);

    ResponseEntity<byte[]> resp = imgController.getDocThumbnail(doc.getId(), null);
    final int ATTACH_FILE_LENGTH = 506;
    assertEquals(ATTACH_FILE_LENGTH, resp.getBody().length);
    verify(recordMgr).save(doc, anyUser);
  }
}
