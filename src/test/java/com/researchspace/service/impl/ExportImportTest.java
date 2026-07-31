package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchivalFileNotExistException;
import com.researchspace.core.util.ResponseUtil;
import com.researchspace.properties.IPropertyHolder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
public class ExportImportTest {

  @TempDir public File folder;

  @Mock private IPropertyHolder properties;

  @InjectMocks private ExportImportImpl exportImpl;

  private MockHttpServletResponse response;
  private StaticMessageSource mockMessageSource;

  @BeforeEach
  public void setUp() throws Exception {
    response = new MockHttpServletResponse();
    exportImpl.setResponseUtil(new ResponseUtil());
    setupMessageSources();
  }

  private void setupMessageSources() {
    mockMessageSource = new StaticMessageSource();
    mockMessageSource.addMessage(
        "archive.download.failure.msg", Locale.getDefault(), "downloadfailed");
    mockMessageSource.addMessage("errors.invalidstringformat", Locale.getDefault(), "badformat");
    exportImpl.setMessageSource(mockMessageSource);
  }

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
    when(properties.getExportFolderLocation()).thenReturn(folder.getAbsolutePath());
    assertThrows(
        ArchivalFileNotExistException.class,
        () -> exportImpl.streamArchiveDownload("file", response));
  }

  @Test
  public void streamArchiveDownloadHappyCase() throws IOException {
    File file = newFile(folder, "file.zip"); // must be zip
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
