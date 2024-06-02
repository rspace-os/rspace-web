package com.researchspace.admin.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.admin.service.impl.FileLocationBasedLogRetriever;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class FileLocationBasedRetrieverTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock IPropertyHolder properties;
  @Mock IPropertyHolder okProperties;

  FileLocationBasedLogRetriever logretriever;
  final File testLogPath = RSpaceTestUtils.getResource("sampleLogs/RSLogs.txt.1");

  @Before
  public void setUp() {
    logretriever = new FileLocationBasedLogRetriever();
    logretriever.setProperties(properties);
    okProperties =
        when(mock(IPropertyHolder.class).getErrorLogFile())
            .thenReturn(testLogPath.getAbsolutePath())
            .getMock();
  }

  @Test(expected = IllegalStateException.class)
  public void checkArgumentValidation() throws IOException {
    when(properties.getErrorLogFile()).thenReturn("unknownPath");
    logretriever.retrieveLastNLogLines(500);
  }

  @Test(expected = IllegalStateException.class)
  public void checkArgumentValidationNoFolder() throws IOException {
    when(properties.getErrorLogFile()).thenReturn("unknownPath");
    logretriever.retrieveLastNLogLines(500);
  }

  @Test
  public void testInvalidLineNumberHandled() throws IOException {
    logretriever.setProperties(okProperties);
    List<String> lines = logretriever.retrieveLastNLogLines(-1); // meaningless number
    assertEquals(FileLocationBasedLogRetriever.DEFAULT_NUM_LINES, lines.size());

    // too big
    List<String> lines2 = logretriever.retrieveLastNLogLines(Integer.MAX_VALUE);
    assertEquals(431, lines2.size());
  }

  @Test
  public void testRetrieveLastNLogLines() throws IOException {
    logretriever.setProperties(okProperties);
    List<String> lines = logretriever.retrieveLastNLogLines(500);
    assertEquals(431, lines.size());
  }
}
