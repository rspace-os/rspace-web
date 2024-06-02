package com.researchspace.admin.service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** Examines server log files */
public interface IServerlogRetriever {

  /**
   * Retrieves the most recent <em>n</em> lines of log files
   *
   * @param numLines
   * @return A {@link List} of error log lines.
   * @throws IOException in the event of an error reading the log file
   * @throws IllegalStateException if specified log is not a readable file.
   */
  List<String> retrieveLastNLogLines(int numLines) throws IOException;

  /** Null object for use in testing/deve environment. */
  public static final IServerlogRetriever NULL_LOG_RETRIEVER =
      new IServerlogRetriever() {

        @Override
        public List<String> retrieveLastNLogLines(int numLines) throws IOException {
          return Collections.emptyList();
        }
      };
}
