package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v2.model.ApiV2AuditEvent;
import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch.FileManifest;
import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch.ReadObserver;
import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch.Request;
import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch.StrictReadException;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.service.UserManager;
import com.researchspace.service.audit.search.AuditTrailActorVisibility;
import com.researchspace.service.audit.search.AuditTrailSearchElement;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import com.researchspace.testutils.TestFactory;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "matching",
        "mismatching",
        "missingAfter",
        "missingBefore",
        "missingWithoutFallback",
        "own"
      })
  void configurationHistoryChecksRelatedTargetsAndCreationTime(String scenario) throws IOException {
    var data = new HashMap<String, Object>();
    data.put("id", scenario.equals("own") ? "BC1" : "bookings:42");
    data.put("bookingConfigurationId", "BC1");
    if (!scenario.startsWith("missing")) {
      data.put(
          "target", Map.of("type", "INSTRUMENT", "id", scenario.equals("mismatching") ? 999 : 552));
    }
    write(
        "RSLogs.txt",
        event(
                scenario.equals("missingBefore")
                    ? "01 Jan 2026 10:00:00,000"
                    : "01 Jan 2026 12:00:00,000",
                data)
            + "\n");
    var request =
        new Request(
            FROM,
            TO,
            Set.of(AuditDomain.UNKNOWN),
            Set.of(AuditAction.CREATE),
            "BC1",
            Set.of(),
            sysadmin,
            10,
            false,
            null,
            Set.of(),
            Set.of("bookingConfigurationId"),
            Map.of("target.type", "INSTRUMENT", "target.id", "552"),
            scenario.equals("missingWithoutFallback")
                ? null
                : Instant.parse("2026-01-01T11:00:00Z"));
    assertEquals(
        scenario.equals("mismatching")
                || scenario.equals("missingBefore")
                || scenario.equals("missingWithoutFallback")
            ? 0
            : 1,
        search(ReadObserver.NONE)
            .search(request, FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs()
            .size());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void actorDirectoryHidesBookingEventsByOutsideUsersUnlessTheResourceBypassesIt(boolean related)
      throws IOException {
    User itemManager = TestFactory.createAnyUser("item-manager");
    UserManager users = Mockito.mock(UserManager.class);
    Mockito.when(users.getViewableUsers(Mockito.eq(itemManager), Mockito.any()))
        .thenReturn(new SearchResultsImpl<User>(List.of(), 0, 0L));
    String payload =
        related
            ? "{\"id\":\"bookings:41\",\"bookingConfigurationId\":\"BC1\"}"
            : "{\"id\":\"BC1\",\"target\":{\"type\":\"INSTRUMENT\",\"id\":552}}";
    write(
        "RSLogs.txt",
        "01 Jan 2026 12:00:00,000 - domain:UNKNOWN action:CREATE"
            + " [{\"data\":"
            + payload
            + "}]"
            + " outside-booker(Outside Booker)\n");
    ApiV2AuditStrictSearch search =
        new ApiV2AuditStrictSearch(
            directory,
            "RSLogs",
            ZoneOffset.UTC,
            new AuditTrailActorVisibility(users),
            ReadObserver.NONE);

    for (boolean bypass : new boolean[] {false, true}) {
      List<ApiV2AuditEvent> results =
          search
              .search(
                  new Request(
                      FROM,
                      TO,
                      Set.of(AuditDomain.UNKNOWN),
                      Set.of(),
                      "BC1",
                      Set.of(),
                      itemManager,
                      10,
                      bypass,
                      null,
                      Set.of(),
                      Set.of("bookingConfigurationId")),
                  FieldSelection.all(),
                  ApiV2AuditStrictSearchTest::project)
              .docs();
      assertEquals(bypass ? 1 : 0, results.size(), "bypass=" + bypass);
    }
  }

  @Test
  void reusedConfigurationIdCannotExposeAnotherTargetsHistory() throws IOException {
    String own =
        event("01 Jan 2026 12:00:00,000", "BC1")
            .replace("\"name\":\"item\"", "\"target\":{\"type\":\"INSTRUMENT\",\"id\":552}");
    write("RSLogs.txt", own.replace("552", "910100009") + "\n" + own + "\n");
    var scanner = search(ReadObserver.NONE);
    var original = request(1);
    for (String id : List.of("552", "910100009")) {
      var restricted =
          new Request(
              original.fromInclusive(),
              original.toExclusive(),
              original.domains(),
              original.actions(),
              original.oid(),
              original.requestedUsernames(),
              original.actor(),
              1,
              false,
              null,
              Set.of(),
              Set.of(),
              Map.of("target.type", "INSTRUMENT", "target.id", id));
      var results =
          scanner
              .search(restricted, FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
              .docs();
      assertEquals(1, results.size());
      assertTrue(results.get(0).payload().toString().contains(id));
    }
  }

  @Test
  void retriesInitialEnumerationFailureInTheSameRequest() throws IOException {
    Path log = write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    var scanner = search(ReadObserver.NONE);
    AtomicInteger reads = new AtomicInteger();
    try (var files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files
          .when(
              () ->
                  Files.readAttributes(
                      Mockito.eq(log),
                      Mockito.eq(BasicFileAttributes.class),
                      Mockito.any(LinkOption[].class)))
          .thenAnswer(
              invocation -> {
                if (reads.getAndIncrement() == 0) {
                  throw new NoSuchFileException(log.toString());
                }
                return invocation.callRealMethod();
              });
      assertEquals(
          1,
          scanner
              .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
              .docs()
              .size());
      assertTrue(reads.get() > 1);
    }
  }

  @Test
  void validEventsAndExactDuplicatesArePreservedAndBlankLinesAreIgnored() throws IOException {
    String event = event("01 Jan 2026 12:00:00,000", "BC1");
    write("RSLogs.txt", "\n" + event + "\n\n" + event + "\n");

    List<ApiV2AuditEvent> results =
        search(ReadObserver.NONE)
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

    assertEquals(2, results.size());
    assertEquals(results.get(0), results.get(1));
  }

  @Test
  void irrelevantAmbiguousBoundaryDoesNotFailAndWholeOverlapIsIncluded() throws IOException {
    write(
        "RSLogs.txt",
        event("25 Oct 2026 02:30:00,000", "BC9")
            + "\n"
            + event("25 Oct 2026 02:30:00,000", "BC1")
            + "\n");
    var scanner = search(ReadObserver.NONE, ZoneId.of("Europe/Berlin"));
    assertEquals(
        1,
        scanner
            .search(
                request(
                    Instant.parse("2026-10-25T00:00:00Z"), Instant.parse("2026-10-25T02:00:00Z")),
                FieldSelection.all(),
                ApiV2AuditStrictSearchTest::project)
            .docs()
            .size());
    write("RSLogs.txt", event("25 Oct 2026 02:30:00,000", "BC9") + "\n");
    assertTrue(
        scanner
            .search(
                request(
                    Instant.parse("2026-10-25T01:00:00Z"), Instant.parse("2026-10-25T02:00:00Z")),
                FieldSelection.all(),
                ApiV2AuditStrictSearchTest::project)
            .docs()
            .isEmpty());
  }

  @Test
  void hiddenDescriptionsCannotMatchText() throws IOException {
    write(
        "RSLogs.txt",
        event("01 Jan 2026 12:00:00,000", "BC1") + "description:[Hidden container IC9]\n");
    var scanner = search(ReadObserver.NONE);
    assertTrue(
        scanner
            .search(request(10, "ic9"), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs()
            .isEmpty());
    assertEquals(
        1,
        scanner
            .search(request(10, "item"), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs()
            .size());
  }

  @Test
  void ambiguousBoundaryTimestampCannotHideSecondFallbackOccurrence() throws IOException {
    write("RSLogs.txt", event("25 Oct 2026 02:30:00,000", "BC1") + "\n");

    StrictReadException error =
        assertThrows(
            StrictReadException.class,
            () ->
                search(ReadObserver.NONE, ZoneId.of("Europe/Berlin"))
                    .search(
                        request(
                            Instant.parse("2026-10-25T01:00:00Z"),
                            Instant.parse("2026-10-25T02:00:00Z")),
                        FieldSelection.all(),
                        ApiV2AuditStrictSearchTest::project)
                    .docs());

    assertEquals("RSLogs.txt", error.getSafeFile());
    assertEquals(1, error.getLineNumber());
  }

  @Test
  void ambiguousInteriorTimestampFailsAfterUnambiguousBoundariesSelectTheFile() throws IOException {
    write(
        "RSLogs.txt",
        event("25 Oct 2026 01:30:00,000", "BC1")
            + "\n"
            + event("25 Oct 2026 02:30:00,000", "BC1")
            + "\n"
            + event("25 Oct 2026 03:30:00,000", "BC1")
            + "\n");

    StrictReadException error =
        assertThrows(
            StrictReadException.class,
            () ->
                search(ReadObserver.NONE, ZoneId.of("Europe/Berlin"))
                    .search(
                        request(
                            Instant.parse("2026-10-25T01:00:00Z"),
                            Instant.parse("2026-10-25T02:00:00Z")),
                        FieldSelection.all(),
                        ApiV2AuditStrictSearchTest::project)
                    .docs());

    assertEquals("RSLogs.txt", error.getSafeFile());
    assertEquals(2, error.getLineNumber());
  }

  @Test
  void nonexistentGapTimestampFailsStrictly() throws IOException {
    write("RSLogs.txt", event("29 Mar 2026 02:30:00,000", "BC1") + "\n");

    assertThrows(
        StrictReadException.class,
        () ->
            search(ReadObserver.NONE, ZoneId.of("Europe/Berlin"))
                .search(
                    request(
                        Instant.parse("2026-03-29T00:00:00Z"),
                        Instant.parse("2026-03-29T03:00:00Z")),
                    FieldSelection.all(),
                    ApiV2AuditStrictSearchTest::project)
                .docs());
  }

  @Test
  void unambiguousTimestampsBeforeAndAfterFallbackUseTheirValidOffsets() throws IOException {
    write(
        "RSLogs.txt",
        event("25 Oct 2026 01:30:00,000", "BC1")
            + "\n"
            + event("25 Oct 2026 03:30:00,000", "BC1")
            + "\n");

    List<Long> timestamps =
        search(ReadObserver.NONE, ZoneId.of("Europe/Berlin"))
            .search(
                request(
                    Instant.parse("2026-10-24T00:00:00Z"), Instant.parse("2026-10-26T00:00:00Z")),
                FieldSelection.all(),
                ApiV2AuditStrictSearchTest::project)
            .docs()
            .stream()
            .map(event -> Instant.parse(event.timestamp()).toEpochMilli())
            .sorted()
            .toList();

    assertEquals(
        List.of(
            Instant.parse("2026-10-24T23:30:00Z").toEpochMilli(),
            Instant.parse("2026-10-25T02:30:00Z").toEpochMilli()),
        timestamps);
  }

  @Test
  void cacheSurvivesCertifiedAppendsOutsideSnapshotButNotHistoricalAppends() throws IOException {
    Path log = write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    AtomicInteger scans = new AtomicInteger();
    var scanner =
        search(
            new ReadObserver() {
              @Override
              public void afterManifest(int attempt, List<FileManifest> files) {
                scans.incrementAndGet();
              }
            });
    assertEquals(
        1,
        scanner
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs()
            .size());
    append(log, event("04 Jan 2026 12:00:00,000", "BC1") + "\n");
    assertEquals(
        1,
        scanner
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs()
            .size());
    assertEquals(1, scans.get());
    append(log, event("02 Jan 2026 12:00:00,000", "BC1") + "\n");
    assertEquals(
        2,
        scanner
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs()
            .size());
    assertEquals(2, scans.get());
  }

  @Test
  void configuredCacheBoundEvictsOlderQueries() throws IOException {
    write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    AtomicInteger scans = new AtomicInteger();
    var scanner =
        new ApiV2AuditStrictSearch(
            directory,
            "RSLogs",
            ZoneOffset.UTC,
            visibility,
            new ReadObserver() {
              @Override
              public void afterManifest(int attempt, List<FileManifest> files) {
                scans.incrementAndGet();
              }
            },
            1,
            1,
            10);
    scanner.search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project).docs();
    scanner.search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project).docs();
    assertEquals(1, scans.get());
    scanner
        .search(request(10, "item"), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
        .docs();
    scanner.search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project).docs();
    assertEquals(3, scans.get());
  }

  @Test
  void cacheReusesScansButRechecksVisibilityAndFileChanges() throws IOException {
    write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    AtomicInteger scans = new AtomicInteger();
    var restricted = new AuditTrailSearchElement();
    restricted.setDomains(EnumSet.of(AuditDomain.UNKNOWN));
    restricted.setActions(EnumSet.of(AuditAction.CREATE));
    restricted.setUsernames(Set.of("alice"));
    visibility = Mockito.mock(AuditTrailActorVisibility.class);
    Mockito.when(visibility.restrict(Mockito.any(), Mockito.eq(sysadmin))).thenReturn(restricted);
    var scanner =
        search(
            new ReadObserver() {
              @Override
              public void afterManifest(int attempt, List<FileManifest> files) {
                scans.incrementAndGet();
              }
            });
    assertEquals(
        1,
        scanner
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs()
            .size());
    assertEquals(
        1,
        scanner
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs()
            .size());
    assertEquals(1, scans.get());
    restricted.setUsernames(Set.of("another-user"));
    assertTrue(
        scanner
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs()
            .isEmpty());
    assertEquals(2, scans.get());
    write(
        "RSLogs.txt",
        event("01 Jan 2026 12:00:00,000", "BC1")
            + "\n"
            + event("02 Jan 2026 12:00:00,000", "BC1")
            + "\n");
    assertTrue(
        scanner
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs()
            .isEmpty());
    assertEquals(3, scans.get());
  }

  @Test
  void certifiedCacheHitDoesNotNeedScanPermits() throws IOException {
    write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    var scanner = search(ReadObserver.NONE);
    var expected =
        scanner
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();
    var permits = (Semaphore) ReflectionTestUtils.getField(scanner, "scanSlots");
    assertTrue(permits.tryAcquire(2));
    try {
      assertEquals(
          expected,
          scanner
              .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
              .docs());
      var refusal =
          assertThrows(
              ResponseStatusException.class,
              () ->
                  scanner
                      .search(request(9), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
                      .docs());
      assertEquals(503, refusal.getStatusCode().value());
      assertEquals("1", refusal.getHeaders().getFirst("Retry-After"));
    } finally {
      permits.release(2);
    }
  }

  @Test
  void searchesOnTheSameInstanceCanReadConcurrently() throws Exception {
    write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    CountDownLatch entered = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    ApiV2AuditStrictSearch search =
        search(
            new ReadObserver() {
              @Override
              public void afterManifest(int attempt, List<FileManifest> manifest) {
                entered.countDown();
                try {
                  if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent search was not released");
                  }
                } catch (InterruptedException error) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(error);
                }
              }
            });
    ReflectionTestUtils.setField(search, "scansPerUser", 2);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first =
          executor.submit(
              () ->
                  search
                      .search(
                          request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
                      .docs());
      var second =
          executor.submit(
              () ->
                  search
                      .search(
                          request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
                      .docs());
      assertTrue(entered.await(5, TimeUnit.SECONDS), "Both searches should enter independently");
      assertThrows(
          ResponseStatusException.class,
          () ->
              search
                  .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
                  .docs());
      release.countDown();
      assertEquals(1, first.get(5, TimeUnit.SECONDS).size());
      assertEquals(1, second.get(5, TimeUnit.SECONDS).size());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
  }

  @Test
  void excludedHistoricalLogIsOpenedOnlyForItsTwoBoundaries() throws IOException {
    Path log =
        write("RSLogs.txt", (event("01 Jan 2020 12:00:00,000", "BC1") + "\n").repeat(10_000));
    try (var channels = Mockito.mockStatic(FileChannel.class, Mockito.CALLS_REAL_METHODS)) {
      assertTrue(
          search(ReadObserver.NONE)
              .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
              .docs()
              .isEmpty());

      channels.verify(
          () -> FileChannel.open(log, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)),
          Mockito.times(2));
    }
  }

  @Test
  void unrelatedApplicationLinesInTheMixedLogAreIgnored() throws IOException {
    write(
        "RSLogs.txt",
        "01 Jan 2026 09:00:00,000 - [/app/workspace] from 127.0.0.1 made by: [alice]\n"
            + event("01 Jan 2026 12:00:00,000", "BC1")
            + "\n"
            + "02 Jan 2026 15:00:00,000 - [FOLDER CREATE id=42&name=notes] alice(Alice Example)\n");

    List<ApiV2AuditEvent> results =
        search(ReadObserver.NONE)
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

    assertEquals(1, results.size());
  }

  @Test
  void emptyActiveFileIsACompleteSnapshotWithNoEvents() throws IOException {
    write("RSLogs.txt", "");

    List<ApiV2AuditEvent> results =
        search(ReadObserver.NONE)
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

    assertTrue(results.isEmpty());
  }

  @Test
  void malformedFirstMiddleAndLastLinesAreFatalWithoutPartialResults() throws IOException {
    String valid = event("01 Jan 2026 12:00:00,000", "BC1");
    String malformedAudit = "01 Jan 2026 11:00:00,000 - domain:UNKNOWN action:CREATE broken";
    for (String body :
        List.of(
            malformedAudit + "\n" + valid + "\n",
            valid + "\n" + malformedAudit + "\n" + valid + "\n",
            valid + "\n" + malformedAudit + "\n")) {
      write("RSLogs.txt", body);

      assertThrows(
          StrictReadException.class,
          () ->
              search(ReadObserver.NONE)
                  .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
                  .docs());
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
              () ->
                  search(ReadObserver.NONE)
                      .search(
                          request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
                      .docs(),
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
        StrictReadException.class,
        () ->
            search(ReadObserver.NONE)
                .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
                .docs());
  }

  @Test
  void validFileFollowedByFailedFileReturnsNoAccumulatedHits() throws IOException {
    write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    write("RSLogs.txt.1", "broken\n");

    assertThrows(
        StrictReadException.class,
        () ->
            search(ReadObserver.NONE)
                .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
                .docs());
  }

  @Test
  void unreadableEligibleFileFailsBothAttempts() throws IOException {
    Path log = write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    Set<PosixFilePermission> original = Files.getPosixFilePermissions(log);
    try {
      Files.setPosixFilePermissions(log, Set.of());
      assertThrows(
          StrictReadException.class,
          () ->
              search(ReadObserver.NONE)
                  .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
                  .docs());
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

    List<ApiV2AuditEvent> results =
        search(appendAfterBoundary)
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

    assertEquals(1, results.size());
  }

  @Test
  void unrelatedApplicationAppendKeepsTheAttemptValid() throws IOException {
    Path log = write("RSLogs.txt", event("01 Jan 2026 12:00:00,000", "BC1") + "\n");
    ReadObserver appendAfterRead =
        new ReadObserver() {
          @Override
          public void afterRead(int attempt, List<FileManifest> manifest) {
            append(
                log,
                "02 Jan 2026 13:00:00,000 - [/app/workspace] from 127.0.0.1 made by: [alice]\n");
          }
        };

    List<ApiV2AuditEvent> results =
        search(appendAfterRead)
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

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

    List<ApiV2AuditEvent> results =
        search(appendEligible)
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

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
        StrictReadException.class,
        () ->
            search(appendEveryAttempt)
                .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
                .docs());
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

    List<ApiV2AuditEvent> results =
        search(replaceOnce)
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

    assertEquals(1, results.size());
    assertEquals(
        Instant.parse("2026-01-02T12:00:00Z").toEpochMilli(),
        Instant.parse(results.get(0).timestamp()).toEpochMilli());
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

    List<ApiV2AuditEvent> results =
        search(truncateOnce)
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

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

    List<ApiV2AuditEvent> results =
        search(rolloverOnce)
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

    assertEquals(2, results.size());
  }

  @Test
  void retainsTheNewestEventsAtTheConfiguredCap() throws IOException {
    write(
        "RSLogs.txt",
        event("01 Jan 2026 12:00:00,000", "BC1")
            + "\n"
            + event("01 Jan 2026 13:00:00,000", "BC1")
            + "\n"
            + event("01 Jan 2026 14:00:00,000", "BC1")
            + "\n");

    List<ApiV2AuditEvent> results =
        search(ReadObserver.NONE)
            .search(request(2), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

    assertEquals(2, results.size());
    assertEquals(
        List.of("2026-01-01T14:00:00Z", "2026-01-01T13:00:00Z"),
        results.stream().map(ApiV2AuditEvent::timestamp).toList());
  }

  @Test
  void textSearchMatchesRecordedValuesBeforeApplyingTheResultCeiling() throws IOException {
    write(
        "RSLogs.txt",
        event("01 Jan 2026 12:00:00,000", "BC1")
            + "\n"
            + event("01 Jan 2026 13:00:00,000", Map.of("id", "BC1", "name", "PayloadMarker"))
            + "\n");

    List<ApiV2AuditEvent> results =
        search(ReadObserver.NONE)
            .search(
                request(1, "payloadmarker"),
                FieldSelection.all(),
                ApiV2AuditStrictSearchTest::project)
            .docs();

    assertEquals(1, results.size());
    assertEquals(
        Instant.parse("2026-01-01T13:00:00Z").toEpochMilli(),
        Instant.parse(results.get(0).timestamp()).toEpochMilli());
  }

  @Test
  void textSearchMatchesDecodedTargetAndPurposeValues() throws IOException {
    write(
        "RSLogs.txt",
        event("01 Jan 2026 12:00:00,000", "BC1")
                .replace(
                    "\"name\":\"item\"", "\"target\":\"related:41\",\"purpose\":\"PurposeMarker\"")
            + "\n");

    assertEquals(
        1,
        search(ReadObserver.NONE)
            .search(
                request(10, "related:41", Set.of("target")),
                FieldSelection.all(),
                ApiV2AuditStrictSearchTest::project)
            .docs()
            .size());
    assertEquals(
        1,
        search(ReadObserver.NONE)
            .search(
                request(10, "purposemarker", Set.of("purpose")),
                FieldSelection.all(),
                ApiV2AuditStrictSearchTest::project)
            .docs()
            .size());
  }

  @Test
  void textSearchDoesNotInspectUnpublishedAuditFields() throws IOException {
    write(
        "RSLogs.txt",
        event(
                "01 Jan 2026 12:00:00,000",
                Map.of("id", "BC1", "name", "Visible", "secret", "PrivateMarker"))
            + "\n");

    assertTrue(
        search(ReadObserver.NONE)
            .search(
                request(10, "privatemarker", Set.of("name")),
                FieldSelection.all(),
                ApiV2AuditStrictSearchTest::project)
            .docs()
            .isEmpty());
  }

  @Test
  void resourceScopeMatchesOnlyTheDecodedTopLevelIdentifier() throws IOException {
    String identifier = "things:41";
    String purpose =
        event("01 Jan 2026 12:00:00,000", Map.of("id", "things:42", "purpose", identifier));
    String nested =
        event(
            "01 Jan 2026 12:00:00,000",
            Map.of("id", "things:42", "target", Map.of("id", identifier)));
    String direct = event("01 Jan 2026 13:00:00,000", identifier.replace(":", "\\u003a"));
    write("RSLogs.txt", purpose + "\n" + nested + "\n" + direct + "\n");
    Request request =
        new Request(
            FROM,
            TO,
            Set.of(AuditDomain.UNKNOWN),
            Set.of(AuditAction.CREATE),
            identifier,
            Set.of(),
            sysadmin,
            1,
            true);

    List<ApiV2AuditEvent> results =
        search(ReadObserver.NONE)
            .search(request, FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

    assertEquals(1, results.size());
    assertEquals(identifier, results.get(0).payload().get("id"));
  }

  @Test
  void blockTailReadPreservesUtf8AcrossBlocksAndTrailingBlankLines() throws IOException {
    String description = "é".repeat(5_000);
    write(
        "RSLogs.txt",
        event("01 Jan 2026 12:00:00,000", "BC1") + " description:[" + description + "]\r\n\r\n");

    List<ApiV2AuditEvent> results =
        search(ReadObserver.NONE)
            .search(request(10), FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

    assertEquals(1, results.size());
    assertEquals(description, results.get(0).description());
  }

  @ParameterizedTest
  @ValueSource(strings = {"bookings:41", "booking-configurations:12"})
  void sameDomainEventsMustMatchTheDecodedTopLevelIdentifier(String identifier) throws IOException {
    String unrelated = event("01 Jan 2026 12:00:00,000", "bookings:42");
    String purpose = unrelated.replace("\"name\":\"item\"", "\"purpose\":\"" + identifier + "\"");
    String nested =
        unrelated.replace("\"name\":\"item\"", "\"target\":{\"id\":\"" + identifier + "\"}");
    String parent =
        unrelated.replace("\"name\":\"item\"", "\"bookingConfigurationId\":\"" + identifier + "\"");
    String direct = event("01 Jan 2026 13:00:00,000", identifier.replace(":", "\\u003a"));
    write("RSLogs.txt", purpose + "\n" + nested + "\n" + parent + "\n" + direct + "\n");
    Request request =
        new Request(
            FROM,
            TO,
            Set.of(AuditDomain.UNKNOWN),
            Set.of(AuditAction.CREATE),
            identifier,
            Set.of(),
            sysadmin,
            1,
            true);

    List<ApiV2AuditEvent> results =
        search(ReadObserver.NONE)
            .search(request, FieldSelection.all(), ApiV2AuditStrictSearchTest::project)
            .docs();

    assertEquals(1, results.size());
    assertEquals(identifier, results.get(0).payload().get("id"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2, 3})
  void capBoundariesAndCertifiedCacheRetainTruncation(int count) throws IOException {
    StringBuilder lines = new StringBuilder();
    for (int index = 0; index < count; index++)
      lines.append(event("01 Jan 2026 12:00:00,000", "BC1")).append("\n");
    write("RSLogs.txt", lines.toString());
    AtomicInteger scans = new AtomicInteger();
    var scanner =
        search(
            new ReadObserver() {
              @Override
              public void afterManifest(int attempt, List<FileManifest> files) {
                scans.incrementAndGet();
              }
            });
    var first =
        scanner.search(request(2), FieldSelection.all(), ApiV2AuditStrictSearchTest::project);
    assertEquals(Math.min(count, 2), first.docs().size());
    assertEquals(count > 2, first.truncated());
    assertEquals(
        first,
        scanner.search(request(2), FieldSelection.all(), ApiV2AuditStrictSearchTest::project));
    assertEquals(1, scans.get());
    scanner.search(
        request(2), FieldSelection.include(Set.of("name")), ApiV2AuditStrictSearchTest::project);
    assertEquals(2, scans.get());
  }

  @Test
  void malformedEligibleDataAfterTheCapStillFails() throws IOException {
    write(
        "RSLogs.txt",
        event("01 Jan 2026 12:00:00,000", "BC1")
            + "\n"
            + event("01 Jan 2026 13:00:00,000", "BC1")
            + "\n"
            + "01 Jan 2026 14:00:00,000 - domain:UNKNOWN action:CREATE [{bad}] admin(Admin)\n"
            + event("01 Jan 2026 15:00:00,000", "BC1")
            + "\n");
    assertThrows(
        StrictReadException.class,
        () ->
            search(ReadObserver.NONE)
                .search(request(1), FieldSelection.all(), ApiV2AuditStrictSearchTest::project));
  }

  @Test
  void ordersVariablePrecisionTimestampsChronologicallyBeforeApplyingTheCap() throws IOException {
    write(
        "RSLogs.txt",
        String.join(
                "\n",
                event("01 Jan 2026 12:00:01,000", "BC1"),
                event("01 Jan 2026 12:00:00,000", "BC1"),
                event("01 Jan 2026 12:00:00,100", "BC1"),
                event("01 Jan 2026 12:00:00,999", "BC1"))
            + "\n");
    var result =
        search(ReadObserver.NONE)
            .search(request(2), FieldSelection.all(), ApiV2AuditStrictSearchTest::project);
    assertEquals(
        List.of("2026-01-01T12:00:01Z", "2026-01-01T12:00:00.999Z"),
        result.docs().stream().map(ApiV2AuditEvent::timestamp).toList());
    assertTrue(result.truncated());
  }

  @Test
  void sameMillisecondEventsSortByDescendingEventIdAndExactDuplicatesRemain() throws IOException {
    var alpha = Map.<String, Object>of("id", "BC1", "name", "Alpha");
    var beta = Map.<String, Object>of("id", "BC1", "name", "Beta");
    write(
        "RSLogs.txt",
        String.join(
                "\n",
                event("01 Jan 2026 12:00:00,000", beta),
                event("01 Jan 2026 12:00:00,000", alpha),
                event("01 Jan 2026 12:00:00,000", alpha))
            + "\n");
    var first = expectedEvent(alpha);
    var second = expectedEvent(beta);
    var expected = List.of(first, first, second);
    var result =
        search(ReadObserver.NONE)
            .search(request(3), FieldSelection.all(), ApiV2AuditStrictSearchTest::project);
    assertEquals(expected, result.docs());
    assertFalse(result.truncated());
  }

  private static ApiV2AuditEvent expectedEvent(Map<String, Object> payload) {
    var event =
        new ApiV2AuditEvent(
            null,
            "2026-01-01T12:00:00Z",
            "alice",
            "Alice Example",
            AuditDomain.UNKNOWN,
            AuditAction.CREATE,
            null,
            payload);
    return event.withEventId(ApiV2AuditLog.eventId(event));
  }

  @Test
  void publicProjectionDeterminesEqualTimestampCutoffAcrossFiles() throws IOException {
    write(
        "RSLogs-z.txt",
        event("01 Jan 2026 12:00:00,000", Map.of("id", "BC1", "name", "Alpha", "secret", "a"))
            + "\n");
    write(
        "RSLogs-a.txt",
        event("01 Jan 2026 12:00:00,000", Map.of("id", "BC1", "name", "Beta", "secret", "b"))
            + "\n"
            + event("01 Jan 2026 12:00:00,000", Map.of("id", "BC1", "name", "Alpha", "secret", "c"))
            + "\n");
    Function<AuditTrailSearchResult, ApiV2AuditEvent> redact =
        raw -> {
          var full = project(raw);
          var visible =
              new ApiV2AuditEvent(
                  null,
                  full.timestamp(),
                  full.username(),
                  full.fullName(),
                  full.domain(),
                  full.action(),
                  null,
                  Map.of("name", full.payload().get("name")));
          return visible.withEventId(ApiV2AuditLog.eventId(visible));
        };
    var scanner = search(ReadObserver.NONE);
    var all = scanner.search(request(10), FieldSelection.all(), redact);
    var capped = scanner.search(request(2), FieldSelection.all(), redact);
    var alpha = expectedEvent(Map.of("name", "Alpha"));
    var beta = expectedEvent(Map.of("name", "Beta"));
    var expected = List.of(alpha, alpha, beta);
    assertEquals(expected, all.docs());
    assertEquals(expected.subList(0, 2), capped.docs());
    assertTrue(capped.truncated());
  }

  private static ApiV2AuditEvent project(AuditTrailSearchResult raw) {
    var source = raw.getEvent();
    var event =
        new ApiV2AuditEvent(
            null,
            Instant.ofEpochMilli(raw.getTimestamp()).toString(),
            source.getSubject(),
            source.getFullName(),
            source.getDomain(),
            source.getAction(),
            source.getDescription(),
            source.getData().getData());
    return event.withEventId(ApiV2AuditLog.eventId(event));
  }

  private ApiV2AuditStrictSearch search(ReadObserver observer) {
    return search(observer, ZoneOffset.UTC);
  }

  private ApiV2AuditStrictSearch search(ReadObserver observer, ZoneId logZone) {
    return new ApiV2AuditStrictSearch(directory, "RSLogs", logZone, visibility, observer);
  }

  private Request request(Instant fromInclusive, Instant toExclusive) {
    return new Request(
        fromInclusive,
        toExclusive,
        Set.of(AuditDomain.UNKNOWN),
        Set.of(AuditAction.CREATE),
        "BC1",
        Set.of(),
        sysadmin,
        10);
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

  private Request request(int ceiling, String search) {
    return request(ceiling, search, Set.of("name"));
  }

  private Request request(int ceiling, String search, Set<String> searchableFields) {
    return new Request(
        FROM,
        TO,
        Set.of(AuditDomain.UNKNOWN),
        Set.of(AuditAction.CREATE),
        "BC1",
        Set.of(),
        sysadmin,
        ceiling,
        false,
        search,
        searchableFields,
        Set.of());
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

  private static String event(String timestamp, Map<String, ?> data) {
    return "%s - domain:UNKNOWN action:CREATE [%s] alice(Alice Example)"
        .formatted(timestamp, JacksonUtil.toJson(Map.of("data", data)));
  }
}
