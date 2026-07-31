package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MediaManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.testutils.TestFactory;
import jakarta.ws.rs.NotFoundException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
public class FilesApiControllerTest {

  @Mock RecordManager recordMgr;
  @Mock IPermissionUtils permissions;
  @Mock MediaManager mediaMgr;
  @Mock IPropertyHolder properties;
  EcatDocumentFile validDocFile;
  StructuredDocument strucDoc;

  private User subject;

  @InjectMocks FilesApiController fileController;

  @BeforeEach
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
    Mockito.lenient().when(properties.getServerUrl()).thenReturn("http://somewhere.com");
  }

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
    assertThrows(NotFoundException.class, () -> fileController.updateFile(1L, mockFile, subject));
    Mockito.verifyNoInteractions(mediaMgr);
  }
}
