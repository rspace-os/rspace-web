package com.researchspace.service.audit.search;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.researchspace.model.audittrail.spring.SpringConfig;
import com.researchspace.testutils.WithSpringContext;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@WithSpringContext
@ContextConfiguration(classes = SpringConfig.class)
@ActiveProfiles("audit-file-cache-test")
public class LogLineCachingTest {

  private @Autowired LogLineContentProvider logLineContentProvider;
  File logFileToCache = new File("src/test/resources/TestResources/sampleLogs/RSLogs.txt.1");
  File logFileNotToCache = new File("src/test/resources/TestResources/sampleLogs/RSLogs.txt");

  @Test
  public void testCaching() throws IOException, ParseException {

    List<LogLine> lines =
        logLineContentProvider.getLinesFromFile(new AuditTrailSearchElement(), logFileToCache);
    assertSame(
        lines,
        logLineContentProvider.getLinesFromFile(new AuditTrailSearchElement(), logFileToCache));

    // just mark to delete cache
    logLineContentProvider.removeFiles(Collections.emptyList());
    assertNotSame(
        lines,
        logLineContentProvider.getLinesFromFile(new AuditTrailSearchElement(), logFileToCache));
  }

  @Test
  public void testCurrFileNeverCached() throws IOException, ParseException {

    List<LogLine> lines =
        logLineContentProvider.getLinesFromFile(new AuditTrailSearchElement(), logFileNotToCache);
    assertNotSame(
        lines,
        logLineContentProvider.getLinesFromFile(new AuditTrailSearchElement(), logFileToCache));
  }
}
