package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MediaManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
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
import org.springframework.mock.web.MockMultipartFile;

public class FilesApiControllerTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Mock RecordManager recordMgr;
  @Mock IPermissionUtils permissions;
  @Mock MediaManager mediaMgr;
  @Mock IPropertyHolder properties;
  EcatDocumentFile validDocFile;
  StructuredDocument strucDoc;

  private User subject;

  @InjectMocks FilesApiController fileController;

  @Before
  public void setUp() throws Exception {
    this.subject = TestFactory.createAnyUser("any");
    StaticMessageSource messages = new StaticMessageSource();
    messages.addMessage("record.inaccessible", Locale.getDefault(), "inaccessible");
    fileController.setMessageSource(new MessageSourceUtils(messages));
    validDocFile = TestFactory.createEcatDocument(1L, subject);
    strucDoc = TestFactory.createAnySD();
    mockBaseUrl();
  }

  private void mockBaseUrl() {
    Mockito.when(properties.getServerUrl()).thenReturn("http://somewhere.com");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void updateFileThrowsNotFoundExceptionIfNotExists() throws Exception {
    MockMultipartFile mockFile = createAnyMultipartFile();
    Mockito.when(recordMgr.getSafeNull(1L)).thenReturn(Optional.empty());
    assertNotFoundExceptionThrown(mockFile);
  }

  @Test
  public void updateFileThrowsNotFoundExceptionIfNotMediaFile() throws Exception {
    MockMultipartFile mockFile = createAnyMultipartFile();
    when(recordMgr.getSafeNull(1L)).thenReturn(Optional.of(strucDoc));
    assertNotFoundExceptionThrown(mockFile);
  }

  @Test
  public void updateFileThrowsNotFoundExceptionIfNotPermitted() throws Exception {
    MockMultipartFile mockFile = createAnyMultipartFile();
    when(recordMgr.getSafeNull(1L)).thenReturn(Optional.of(validDocFile));
    when(permissions.isPermitted(validDocFile, PermissionType.WRITE, subject)).thenReturn(false);
    assertNotFoundExceptionThrown(mockFile);
  }

  @Test
  public void updateFileSuccess() throws Exception {
    MockMultipartFile mockFile = createAnyMultipartFile();
    when(recordMgr.getSafeNull(1L)).thenReturn(Optional.of(validDocFile));
    when(permissions.isPermitted(validDocFile, PermissionType.WRITE, subject)).thenReturn(true);
    Mockito.when(
            mediaMgr.saveMediaFile(
                Mockito.any(InputStream.class),
                Mockito.eq(1L),
                Mockito.eq(mockFile.getOriginalFilename()),
                Mockito.eq(mockFile.getOriginalFilename()),
                Mockito.eq(null),
                Mockito.eq(null),
                Mockito.eq(null),
                Mockito.eq(subject)))
        .thenReturn(validDocFile);
    assertNotNull(fileController.updateFile(1L, mockFile, subject));
  }

  private MockMultipartFile createAnyMultipartFile() {
    return new MockMultipartFile("afile.dat", new byte[] {1, 2, 3, 4, 5});
  }

  private void assertNotFoundExceptionThrown(MockMultipartFile mockFile) throws Exception {
    CoreTestUtils.assertExceptionThrown(
        () -> fileController.updateFile(1L, mockFile, subject), NotFoundException.class);
    Mockito.verifyNoInteractions(mediaMgr);
  }
}
