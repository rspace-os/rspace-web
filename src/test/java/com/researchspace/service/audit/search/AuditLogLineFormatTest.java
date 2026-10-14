package com.researchspace.service.audit.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch;
import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch.Request;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditData;
import org.junit.jupiter.api.Test;
import com.researchspace.testutils.TestFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuditLogLineFormatTest {
  @TempDir Path directory;

  static Stream<Arguments> lines() {
    return Stream.of(
        Arguments.of("alice#1", "CREATE", null),
        Arguments.of("sys#admin->alice#1", "CREATE", null),
        Arguments.of("a->b", "CREATE", null),
        Arguments.of("alice", "CREATE", ""),
        Arguments.of("alice", "COPY", "Legacy copy"),
        Arguments.of("alice", "WRITE", "Removed [user9]."));
  }

  @Test
  void preservesMultilineBookingCancellationReasonsInJsonSnapshots() {
    String line =
        "01 Jan 2026 12:00:00,000 - domain:BOOKING action:WRITE"
            + " [{\"data\":{\"id\":\"bookings:41\","
            + "\"state\":\"CANCELLED\","
            + "\"cancellationReason\":\"First line\\nSecond line\"}}]"
            + " alice(Alice Example)";
    var parsed = AuditLogLineFormat.parse(line, malformed -> fail(malformed)).orElseThrow();
    assertEquals(
        "First line\nSecond line",
        AuditData.fromJson(parsed.data()).getData().get("cancellationReason"));
  }

  @ParameterizedTest
  @MethodSource("lines")
  void callersAgreeOnAcceptedLines(String username, String action, String description)
      throws Exception {
    String line =
        "01 Jan 2026 12:00:00,000 - domain:RECORD action:"
            + action
            + " [{\"data\":{\"id\":\"SD1\"}}] "
            + username
            + "(Alice Example)"
            + (description == null ? "" : "description:[" + description + "]");
    var fields = AuditLogLineFormat.parse(line, malformed -> fail(malformed)).orElseThrow();
    assertEquals("01 Jan 2026 12:00:00,000 ", fields.timestamp());
    assertEquals(username, fields.username());
    assertEquals(description, fields.description());
    assertEquals("COPY".equals(action) ? "DUPLICATE" : action, fields.action());

    var legacy =
        new LogLineParser()
            .parseLine(
                line, new SimpleDateFormat(BasicLogQuerySearcher.DATE_FORMAT, Locale.ENGLISH));
    Files.writeString(directory.resolve("RSLogs.txt"), line + "\n");
    var strict = new ApiV2AuditStrictSearch(directory.toString(), null);
    var events =
        strict.search(
            new Request(
                Instant.parse("2025-12-30T00:00:00Z"),
                Instant.parse("2026-01-03T00:00:00Z"),
                Set.of(AuditDomain.RECORD),
                Set.of(AuditAction.values()),
                "SD1",
                Set.of(),
                TestFactory.createAnyUser("sysadmin"),
                10,
                true));
    assertEquals(1, events.size());
    var event = events.get(0).getEvent();
    assertEquals(legacy.getDate().getTime(), events.get(0).getTimestamp());
    assertEquals(legacy.getDomain(), event.getDomain());
    assertEquals(legacy.getAction(), event.getAction());
    assertEquals(legacy.getUsername(), event.getSubject());
    assertEquals(legacy.getFullname(), event.getFullName());
    assertEquals(legacy.getDescription(), event.getDescription());
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"malformed", "01 Jan 2026 12:00:00,000 - domain:RECORD action:CREATE broken"})
  void callersChooseMalformedLinePolicy(String line) {
    assertTrue(AuditLogLineFormat.parse(line, malformed -> {}).isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AuditLogLineFormat.parse(
                line,
                malformed -> {
                  throw new IllegalArgumentException("Malformed audit line");
                }));
  }
}
