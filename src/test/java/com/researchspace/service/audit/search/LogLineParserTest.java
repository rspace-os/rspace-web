package com.researchspace.service.audit.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.text.SimpleDateFormat;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class LogLineParserTest {
  private final LogLineParser parser = new LogLineParser();

  @Test
  void skipsMalformedLines() throws Exception {
    assertNull(parser.parseLine("malformed", format()));
  }

  @Test
  void retainsLenientTimestampParsing() throws Exception {
    LogLine line =
        parser.parseLine(
            "32 Jan 2026 12:00:00,000 - domain:RECORD action:CREATE [{\"data\":{}}] alice(Alice)",
            format());
    assertEquals(format().parse("01 Feb 2026 12:00:00,000"), line.getDate());
  }

  private SimpleDateFormat format() {
    return new SimpleDateFormat(BasicLogQuerySearcher.DATE_FORMAT, Locale.ENGLISH);
  }
}
