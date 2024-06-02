package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.core.testutil.JavaxValidatorTest;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.IMediaFactory;
import com.researchspace.service.MediaManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.webapp.controller.ImageController.EditedImageData;
import com.researchspace.webapp.controller.ImageController.RotationConfig;
import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.apache.shiro.authz.AuthorizationException;
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
  RotationConfig cfg;

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock UserManager usrMgr;
  @Mock RecordManager recordMgr;
  @Mock AuditTrailService auditService;
  @Mock MediaManager mediaMgr;
  @Mock IPermissionUtils permUtils;
  @Mock BaseRecordManager baseRecordManager;
  @Mock FileStore fStore;
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
  public void editImageSuccess() throws IOException {
    Principal p = anyUser::getUsername;
    // setup  fixture
    long srcId = 1L, newId = 2L;
    EditedImageData data = new EditedImageData(srcId, "base64data");
    EcatImage src = makeImage(srcId);
    EcatImage newImg = makeImage(newId);
    // given
    setUpMocks(srcId, data, src, newImg);
    when(permUtils.isRecordAccessPermitted(anyUser, src, PermissionType.READ)).thenReturn(true);
    // when
    AjaxReturnObject<Long> result = imgController.saveEditedImage(data);
    // then
    assertEquals(newId, result.getData());
    verify(auditService).notify(Mockito.any(GenericEvent.class));
  }

  @Test
  public void editImageUnauthorised() throws Exception {
    Principal p = anyUser::getUsername;
    // setup  fixture
    long srcId = 1L, newId = 2L;
    EditedImageData data = new EditedImageData(srcId, "base64data");
    EcatImage src = makeImage(srcId);
    EcatImage newImg = makeImage(newId);
    // given
    setUpMocks(srcId, data, src, newImg);
    when(permUtils.isRecordAccessPermitted(anyUser, src, PermissionType.READ)).thenReturn(false);
    // when
    assertExceptionThrown(() -> imgController.saveEditedImage(data), AuthorizationException.class);
    verify(auditService, never()).notify(Mockito.any(GenericEvent.class));
    verify(mediaMgr, never()).saveEditedImage(src, data.getImageBase64(), anyUser);
  }

  private void setUpMocks(long srcId, EditedImageData data, EcatImage src, EcatImage newImg)
      throws IOException {
    when(usrMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
    when(recordMgr.getEcatImage(srcId, false)).thenReturn(src);
    when(mediaMgr.saveEditedImage(src, data.getImageBase64(), anyUser)).thenReturn(newImg);
  }

  EcatImage makeImage(long id) {
    EcatImage img = TestFactory.createEcatImage(id);
    img.setOwner(anyUser);
    return img;
  }

  @Test
  public void rotateGalleriesCfgValidation() {
    cfg = new RotationConfig();
    cfg.setIdsToRotate(Collections.emptyList());
    cfg.setTimesToRotate((byte) -1);
    assertNErrors(cfg, 2);
    cfg.setTimesToRotate((byte) 1);
    assertNErrors(cfg, 1);
    cfg.setIdsToRotate(toList(1L));
    assertNErrors(cfg, 0);
    cfg.setTimesToRotate((byte) 5);
    assertNErrors(cfg, 1);
    cfg.setIdsToRotate(LongStream.range(0, 100).boxed().collect(Collectors.toList()));
    assertNErrors(cfg, 2);
  }

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
