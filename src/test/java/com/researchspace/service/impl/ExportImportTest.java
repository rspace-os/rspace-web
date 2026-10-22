package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchivalFileNotExistException;
import com.researchspace.core.util.ResponseUtil;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.model.User;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.field.LocalizedIOException;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.archive.PdfWordExportManager;
import com.researchspace.service.archive.export.ExportFailureException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
public class ExportImportTest {

  @TempDir public File folder;

  @Mock private IPropertyHolder properties;
  @Mock private CommunicationManager commMgr;
  @Mock private PdfWordExportManager pdfWordExportManager;

  @InjectMocks private ExportImportImpl exportImpl;

  private MockHttpServletResponse response;

  @BeforeEach
  public void setUp() throws Exception {
    response = new MockHttpServletResponse();
    exportImpl.setResponseUtil(new ResponseUtil());
    exportImpl.setMessageSource(new MessageSourceUtils(new JsonMessageSource()));
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void validateFileNameToDownLoadBadArchive() throws IOException {
    assertThrows(
        IllegalArgumentException.class,
        () -> exportImpl.streamArchiveDownload("../../badfile.test", response));
  }

  @Test
  public void validateFileNameCannotBeEmpty() throws IOException {
    assertThrows(
        IllegalArgumentException.class, () -> exportImpl.streamArchiveDownload("", response));
  }

  @Test
  public void downloadNonexistentFile() throws IOException {
    File file = newFile(folder, "file");
    FileUtils.write(file, "some data", StandardCharsets.UTF_8);
    String exportFolder = folder.getAbsolutePath();
    when(properties.getExportFolderLocation()).thenReturn(exportFolder);

    assertThrows(
        ArchivalFileNotExistException.class,
        () -> exportImpl.streamArchiveDownload("file", response));
  }

  @Test
  public void downloadFailureIsNotReportedAsMissingFile() throws IOException {
    File file = newFile(folder, "file.zip");
    FileUtils.write(file, "some data", StandardCharsets.UTF_8);
    when(properties.getExportFolderLocation()).thenReturn(folder.getAbsolutePath());
    HttpServletResponse failingResponse = mock(HttpServletResponse.class);
    when(failingResponse.getOutputStream())
        .thenReturn(
            new ServletOutputStream() {
              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setWriteListener(WriteListener writeListener) {}

              @Override
              public void write(int value) throws IOException {
                throw new IOException("simulated output failure");
              }
            });

    try {
      exportImpl.streamArchiveDownload("file.zip", failingResponse);
      fail("Expected an export failure");
    } catch (ExportFailureException e) {
      assertEquals(
          "Export could not be downloaded. Contact us at support@researchspace.com for help with"
              + " export.",
          e.getMessage());
    }
  }

  @Test
  public void wrappedLocalizedIoFailureIsTranslatedInExportNotification() throws IOException {
    LocalizedIOException tiffFailure =
        new LocalizedIOException(
            "validation.imageConversion.sourceUnreadable", new File("specimen.tiff"));
    ExportFailureException exportFailure = new ExportFailureException(tiffFailure);

    String notificationMessage = exportFailureNotification(exportFailure);

    assertEquals(
        "Your export [TIFF export] failed for the following reason: Can't read file:specimen.tiff",
        notificationMessage);
    assertFalse(notificationMessage.contains("validation.imageConversion.sourceUnreadable"));
    assertFalse(notificationMessage.contains("LocalizedIOException"));
  }

  @Test
  public void ordinaryExportFailureKeepsTheOriginalMessage() throws IOException {
    ExportFailureException exportFailure =
        new ExportFailureException(new IOException("ordinary failure"));

    assertEquals(
        "Your export [TIFF export] failed for the following reason: " + exportFailure.getMessage(),
        exportFailureNotification(exportFailure));
  }

  private String exportFailureNotification(ExportFailureException exportFailure)
      throws IOException {
    doThrow(exportFailure)
        .when(pdfWordExportManager)
        .doExport(any(), any(), any(), any(), any(), any());
    ExportToFileConfig config = new ExportToFileConfig();
    config.setExportName("TIFF export");
    User exporter = new User("exporter");

    assertThrows(
        ExportFailureException.class,
        () ->
            exportImpl.asyncExportSelectionToPdf(
                new Long[0], new String[0], new String[0], config, exporter));

    ArgumentCaptor<String> notificationMessage = ArgumentCaptor.forClass(String.class);
    verify(commMgr)
        .systemNotify(
            eq(NotificationType.PROCESS_COMPLETED),
            notificationMessage.capture(),
            eq(exporter.getUsername()),
            eq(true));
    return notificationMessage.getValue();
  }

  @Test
  public void streamArchiveDownloadHappyCase() throws IOException {
    File file = newFile(folder, "file.zip");
    final String EXPECTED_RESPONSE = "some data";

    FileUtils.write(file, "some data", StandardCharsets.UTF_8);

    when(properties.getExportFolderLocation()).thenReturn(folder.getAbsolutePath());

    exportImpl.streamArchiveDownload("file", response);
    assertEquals(EXPECTED_RESPONSE, response.getContentAsString());

    exportImpl.streamArchiveDownload("file.zip", response);
    assertEquals(EXPECTED_RESPONSE + EXPECTED_RESPONSE, response.getContentAsString());
    // once for each case - with or without zip suffix
    verify(properties, times(2)).getExportFolderLocation();
  }

  private static File newFile(File parent, String child) throws IOException {
    File result = new File(parent, child);
    result.createNewFile();
    return result;
  }
}
