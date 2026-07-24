package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchivalFileNotExistException;
import com.researchspace.core.util.ResponseUtil;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.archive.export.ExportFailureException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletResponse;

public class ExportImportTest {

  @Rule public MockitoRule mockery = MockitoJUnit.rule();

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Mock private IPropertyHolder properties;

  @InjectMocks private ExportImportImpl exportImpl;

  private MockHttpServletResponse response;

  @Before
  public void setUp() throws Exception {
    response = new MockHttpServletResponse();
    exportImpl.setResponseUtil(new ResponseUtil());
    exportImpl.setMessageSource(new MessageSourceUtils(new JsonMessageSource()));
  }

  @After
  public void tearDown() throws Exception {}

  @Test(expected = IllegalArgumentException.class)
  public void validateFileNameToDownLoadBadArchive() throws IOException {
    exportImpl.streamArchiveDownload("../../badfile.test", response);
  }

  @Test(expected = IllegalArgumentException.class)
  public void validateFileNameCannotBeEmpty() throws IOException {
    exportImpl.streamArchiveDownload("", response);
  }

  @Test(expected = ArchivalFileNotExistException.class)
  public void downloadNonexistentFile() throws IOException {
    File file = folder.newFile("file");
    FileUtils.write(file, "some data", StandardCharsets.UTF_8);
    when(properties.getExportFolderLocation()).thenReturn(folder.getRoot().getAbsolutePath());
    exportImpl.streamArchiveDownload("file", response);
  }

  @Test
  public void downloadFailureIsNotReportedAsMissingFile() throws IOException {
    File file = folder.newFile("file.zip");
    FileUtils.write(file, "some data", StandardCharsets.UTF_8);
    when(properties.getExportFolderLocation()).thenReturn(folder.getRoot().getAbsolutePath());
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
  public void streamArchiveDownloadHappyCase() throws IOException {
    File file = folder.newFile("file.zip"); // must be zip
    final String EXPECTED_RESPONSE = "some data";

    FileUtils.write(file, "some data", StandardCharsets.UTF_8);

    when(properties.getExportFolderLocation()).thenReturn(folder.getRoot().getAbsolutePath());

    exportImpl.streamArchiveDownload("file", response);
    assertEquals(EXPECTED_RESPONSE, response.getContentAsString());

    exportImpl.streamArchiveDownload("file.zip", response);
    assertEquals(EXPECTED_RESPONSE + EXPECTED_RESPONSE, response.getContentAsString());
    // once for each case - with or without zip suffix
    verify(properties, times(2)).getExportFolderLocation();
  }
}
