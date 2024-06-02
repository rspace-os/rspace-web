package com.researchspace.admin.service.impl;

import com.researchspace.admin.service.IServerlogRetriever;
import com.researchspace.properties.IPropertyHolder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Uses reverse-line iterator to retrieve latest log files */
public class FileLocationBasedLogRetriever implements IServerlogRetriever {

  public static final int DEFAULT_NUM_LINES = 300;

  public static final int MAX_NUM_LINES = 50000;

  private Logger logger = LoggerFactory.getLogger(FileLocationBasedLogRetriever.class);

  private IPropertyHolder properties;

  @Autowired
  public void setProperties(IPropertyHolder properties) {
    this.properties = properties;
  }

  @Override
  public List<String> retrieveLastNLogLines(int numLines) throws IOException {
    numLines = validateNumLines(numLines);
    String catalinaOutFile = properties.getErrorLogFile();
    File logFile = new File(catalinaOutFile);
    assertLogFileExistsAndReadable(logFile);

    String line = null;
    int count = 0;
    List<String> lines = new ArrayList<String>();
    try (ReversedLinesFileReader fr = new ReversedLinesFileReader(logFile)) {
      while (count < numLines && (line = fr.readLine()) != null) {
        lines.add(line);
        count++;
      }
    }
    Collections.reverse(lines);
    return lines;
  }

  private void assertLogFileExistsAndReadable(File logFile) {
    if (!logFile.exists()) {
      throw new IllegalStateException("Log file [" + logFile + " does not exist");
    }
    if (!logFile.isFile() || !logFile.canRead()) {
      throw new IllegalStateException("Log file [" + logFile + " is not a readable file");
    }
  }

  private int validateNumLines(int numLines) {
    if (numLines < 1) {
      logger.warn("Invalid log length(" + numLines + "), using default of " + DEFAULT_NUM_LINES);
      numLines = DEFAULT_NUM_LINES;
    } else if (numLines > MAX_NUM_LINES) {
      logger.warn(
          "requested log length("
              + numLines
              + " exceeds maximum, using max length of "
              + MAX_NUM_LINES);
      numLines = MAX_NUM_LINES;
    }
    return numLines;
  }
}
