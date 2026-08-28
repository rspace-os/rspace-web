package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch.FileManifest;
import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch.ReadObserver;
import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch.Request;
import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch.StrictReadException;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.service.UserManager;
import com.researchspace.service.audit.search.AuditTrailActorVisibility;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import com.researchspace.testutils.TestFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class ApiV2AuditStrictSearchTest {

  private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-01-03T00:00:00Z");

  @TempDir private Path directory;

  private User sysadmin;
  private AuditTrailActorVisibility visibility;

  @BeforeEach
  void setUp() {
    sysadmin = TestFactory.createAnyUser("sysadmin");
    sysadmin.addRole(Role.SYSTEM_ROLE);
    visibility = new AuditTrailActorVisibility(Mockito.mock(UserManager.class));
  }

  @Test
  void validEventsAndExactDuplicatesArePreservedAndBlankLinesAreIgnored() throws IOException {
    String event = event("01 Jan 2026 12:00:00,000", "BC1");
    write("RSLogs.txt", "\n" + event + "\n\n" + event + "\n");

    List<AuditTrailSearchResult> results = search(ReadObserver.NONE, 10).search(request(10));

    assertEquals(2, results.size());
    assertEquals(results.get(0), results.get(1));
  }

  @Test
  void emptyActiveFileIsACompleteSnapshotWithNoEvents() throws IOException {
    write("RSLogs.txt", "");

    List<AuditTrailSearchResult> results = search(ReadObserver.NONE, 10).search(request(10));

    assertTrue(results.isEmpty());
  }

  @Test
  void malformedFirstMiddleAndLastLinesAreFatalWithoutPartialResults() throws IOException {
    String valid = event("01 Jan 2026 12:00:00,000", "BC1");
    for (String body :
        List.of(
            "not an audit event\n" + valid + "\n",
            valid + "\nnot an audit event\n" + valid + "\n",
            valid + "\nnot an audit event\n")) {
      write("RSLogs.txt", body);

      assertThrows(
          StrictReadException.class, () -> search(ReadObserver.NONE, 10).search(request(10)));
    }
  }

  @Test
  void invalidTimestampUnknownEnumsAndMalformedJsonAreFatal() throws IOException {
    for (String line :
        List.of(
            event("32 Jan 2026 12:00:00,000", "BC1"),
            event("01 Jan 2026 12:00:00,000", "BC1").replace("domain:UNKNOWN", "domain:NOPE"),
            event("01 Jan 2026 12:00:00,000", "BC1").replace("action:CREATE", "action:NOPE"),
            event("01 Jan 2026 12:00:00,000", "BC1")
                .replace("{\"data\":{\"id\":\"BC1\",\"name\":\"item\"}}", "not-json"))) {
      write("RSLogs.txt", line + "\n");

      StrictReadException error =
          assertThrows(
              StrictReadException.class,
              () -> search(ReadObserver.NONE, 10).search(request(10)),
              line);
      assertEquals("RSLogs.txt", error.getSafeFile());
      assertTrue(error.getLineNumber() >= 1);
      assertTrue(!error.getMessage().contains(line));
    }
  }

  @Test
  void truncatedEligibleRecordIsFatal() throws IOException {
    write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1"));

    assertThrows(
        StrictReadException.class, () -> search(ReadObserver.NONE, 10).search(request(10)));
  }

  @Test
  void validFileFollowedByFailedFileReturnsNoAccumulatedHits() throws IOException {
    write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    write("RSLogs.txt.1", "broken\n");

    assertThrows(
        StrictReadException.class, () -> search(ReadObserver.NONE, 10).search(request(10)));
  }

  @Test
  void unreadableEligibleFileFailsBothAttempts() throws IOException {
    Path log = write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    Set<PosixFilePermission> original = Files.getPosixFilePermissions(log);
    try {
      Files.setPosixFilePermissions(log, Set.of());
      assertThrows(
          StrictReadException.class, () -> search(ReadObserver.NONE, 10).search(request(10)));
    } finally {
      Files.setPosixFilePermissions(log, original);
    }
  }

  @Test
  void harmlessPostBoundaryAppendKeepsTheAttemptValid() throws IOException {
    Path log = write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    ReadObserver appendAfterBoundary =
        new ReadObserver() {
          @Override
          public void afterRead(int attempt, List<FileManifest> manifest) {
            if (attempt == 1) {
              append(log, event("03 Jan 2026 00:00:00,000", "BC1") + "\n");
            }
          }
        };

    List<AuditTrailSearchResult> results = search(appendAfterBoundary, 10).search(request(10));

    assertEquals(1, results.size());
  }

  @Test
  void eligibleAppendDiscardsTheAttemptAndOneFreshRetrySucceeds() throws IOException {
    Path log = write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    ReadObserver appendEligible =
        new ReadObserver() {
          @Override
          public void afterRead(int attempt, List<FileManifest> manifest) {
            if (attempt == 1) {
              append(log, event("02 Jan 2026 12:00:00,000", "BC1") + "\n");
            }
          }
        };

    List<AuditTrailSearchResult> results = search(appendEligible, 10).search(request(10));

    assertEquals(2, results.size());
  }

  @Test
  void twoUnstableAttemptsReturnUnavailable() throws IOException {
    Path log = write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    AtomicInteger sequence = new AtomicInteger(2);
    ReadObserver appendEveryAttempt =
        new ReadObserver() {
          @Override
          public void afterRead(int attempt, List<FileManifest> manifest) {
            append(
                log,
                event("02 Jan 2026 12:00:0%d,000".formatted(sequence.getAndIncrement()), "BC1")
                    + "\n");
          }
        };

    assertThrows(
        StrictReadException.class, () -> search(appendEveryAttempt, 10).search(request(10)));
  }

  @Test
  void replacementBetweenReadAndCertificationRetriesWithoutMixingAttempts() throws IOException {
    Path log = write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    ReadObserver replaceOnce =
        new ReadObserver() {
          @Override
          public void afterRead(int attempt, List<FileManifest> manifest) {
            if (attempt == 1) {
              try {
                Path replacement =
                    write("replacement", event("02 Jan 2026 12:00:00,000", "BC1") + "\n");
                Files.move(replacement, log, StandardCopyOption.REPLACE_EXISTING);
              } catch (IOException ex) {
                throw new AssertionError(ex);
              }
            }
          }
        };

    List<AuditTrailSearchResult> results = search(replaceOnce, 10).search(request(10));

    assertEquals(1, results.size());
    assertEquals(
        Instant.parse("2026-01-02T12:00:00Z").toEpochMilli(), results.get(0).getTimestamp());
  }

  @Test
  void truncationDuringCertificationRetriesFromTheFreshEmptyManifest() throws IOException {
    Path log = write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    ReadObserver truncateOnce =
        new ReadObserver() {
          @Override
          public void afterRead(int attempt, List<FileManifest> manifest) {
            if (attempt == 1) {
              try {
                Files.newByteChannel(log, StandardOpenOption.WRITE).truncate(0).close();
              } catch (IOException ex) {
                throw new AssertionError(ex);
              }
            }
          }
        };

    List<AuditTrailSearchResult> results = search(truncateOnce, 10).search(request(10));

    assertTrue(results.isEmpty());
  }

  @Test
  void rolloverBetweenEnumerationAndOpenRetriesFromTheNewManifest() throws IOException {
    write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    ReadObserver rolloverOnce =
        new ReadObserver() {
          @Override
          public void beforeOpen(int attempt, FileManifest file) {
            if (attempt == 1 && !Files.exists(directory.resolve("RSLogs.txt.1"))) {
              try {
                write("RSLogs.txt.1", event("02 Jan 2026 12:00:00,000", "BC1") + "\n");
              } catch (IOException ex) {
                throw new AssertionError(ex);
              }
            }
          }
        };

    List<AuditTrailSearchResult> results = search(rolloverOnce, 10).search(request(10));

    assertEquals(2, results.size());
  }

  @Test
  void collectionStopsAtCeilingPlusOne() throws IOException {
    write(
        "RSLogs.txt",
        event("01 Jan 2026 12:00:00,000", "BC1")
            + "\n"
            + event("01 Jan 2026 13:00:00,000", "BC1")
            + "\n"
            + event("01 Jan 2026 14:00:00,000", "BC1")
            + "\n");

    List<AuditTrailSearchResult> results = search(ReadObserver.NONE, 2).search(request(2));

    assertEquals(3, results.size());
  }

  private ApiV2AuditStrictSearch search(ReadObserver observer, int ceiling) {
    return new ApiV2AuditStrictSearch(directory, "RSLogs", ZoneOffset.UTC, visibility, observer);
  }

  private Request request(int ceiling) {
    return new Request(
        FROM,
        TO,
        Set.of(AuditDomain.UNKNOWN),
        Set.of(AuditAction.CREATE),
        "BC1",
        Set.of(),
        sysadmin,
        ceiling);
  }

  private Path write(String filename, String content) throws IOException {
    return Files.writeString(directory.resolve(filename), content, StandardCharsets.UTF_8);
  }

  private static void append(Path path, String content) {
    try {
      Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    } catch (IOException ex) {
      throw new AssertionError(ex);
    }
  }

  private static String event(String timestamp, String id) {
    return "%s - domain:UNKNOWN action:CREATE [{\"data\":{\"id\":\"%s\",\"name\":\"item\"}}] alice(Alice Example)"
        .formatted(timestamp, id);
  }
}
