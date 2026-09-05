package com.researchspace.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.admin.service.impl.FileLocationBasedLogRetriever;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FileLocationBasedRetrieverTest {
  @Mock IPropertyHolder properties;

  FileLocationBasedLogRetriever logretriever;
  final File testLogPath = RSpaceTestUtils.getResource("sampleLogs/RSLogs.txt.1");

  @BeforeEach
  public void setUp() {
    logretriever = new FileLocationBasedLogRetriever();
    logretriever.setProperties(properties);
  }

  private IPropertyHolder okProperties() {
    return when(mock(IPropertyHolder.class).getErrorLogFile())
        .thenReturn(testLogPath.getAbsolutePath())
        .getMock();
  }

  @Test
  public void checkArgumentValidation() throws IOException {
    when(properties.getErrorLogFile()).thenReturn("unknownPath");
    assertThrows(IllegalStateException.class, () -> logretriever.retrieveLastNLogLines(500));
  }

  @Test
  public void checkArgumentValidationNoFolder() throws IOException {
    when(properties.getErrorLogFile()).thenReturn("unknownPath");
    assertThrows(IllegalStateException.class, () -> logretriever.retrieveLastNLogLines(500));
  }

  @Test
  public void testInvalidLineNumberHandled() throws IOException {
    logretriever.setProperties(okProperties());
    List<String> lines = logretriever.retrieveLastNLogLines(-1); // meaningless number
    assertEquals(FileLocationBasedLogRetriever.DEFAULT_NUM_LINES, lines.size());

    // too big
    List<String> lines2 = logretriever.retrieveLastNLogLines(Integer.MAX_VALUE);
    assertEquals(431, lines2.size());
  }

  @Test
  public void testRetrieveLastNLogLines() throws IOException {
    logretriever.setProperties(okProperties());
    List<String> lines = logretriever.retrieveLastNLogLines(500);
    assertEquals(431, lines.size());
  }
}
