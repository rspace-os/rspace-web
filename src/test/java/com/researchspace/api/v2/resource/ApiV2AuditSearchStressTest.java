package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v2.model.ApiV2AuditEvent;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.service.UserManager;
import com.researchspace.service.audit.search.AuditTrailActorVisibility;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import com.researchspace.testutils.TestFactory;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

@EnabledIfSystemProperty(named = "audit.stress", matches = "true")
class ApiV2AuditSearchStressTest {

  private static final List<Integer> MATCH_COUNTS = List.of(1_000, 5_000, 10_000, 25_000, 50_000);
  private static final List<Integer> CONCURRENCIES = List.of(1, 4, 8);
  private static final int SAMPLES = 3;
  private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-01-03T00:00:00Z");

  @TempDir private Path directory;

  @Test
  void measuresFirstCollectionAndLaterFingerprintVerification() throws Exception {
    long maxHeap = Runtime.getRuntime().maxMemory();
    int cpuCount = Runtime.getRuntime().availableProcessors();
    long requestTimeoutMillis = Long.getLong("audit.stress.requestTimeoutMillis", 30_000L);
    String storageType = Files.getFileStore(directory).type();
    System.out.printf(
        Locale.ROOT,
        "AUDIT_STRESS|jdk=%s|heapMaxBytes=%d|cpu=%d|storage=%s|requestTimeoutMs=%d%n",
        Runtime.version(),
        maxHeap,
        cpuCount,
        storageType,
        requestTimeoutMillis);

    User sysadmin = TestFactory.createAnyUser("audit-stress-admin");
    sysadmin.addRole(Role.SYSTEM_ROLE);
    AuditTrailActorVisibility visibility =
        new AuditTrailActorVisibility(Mockito.mock(UserManager.class));
    List<Measurement> measurements = new ArrayList<>();

    for (int matchCount : MATCH_COUNTS) {
      clearLogs();
      writeLogs(matchCount);
      ApiV2AuditStrictSearch search =
          new ApiV2AuditStrictSearch(
              directory,
              "RSLogs",
              ZoneOffset.UTC,
              visibility,
              ApiV2AuditStrictSearch.ReadObserver.NONE);
      ApiV2AuditStrictSearch.Request request =
          new ApiV2AuditStrictSearch.Request(
              FROM,
              TO,
              Set.of(AuditDomain.UNKNOWN),
              Set.of(AuditAction.WRITE),
              "BC-STRESS",
              Set.of(),
              sysadmin,
              matchCount);
      scanFingerprint(search, request);

      for (int concurrency : CONCURRENCIES) {
        Measurement measurement = measure(search, request, matchCount, concurrency);
        measurements.add(measurement);
        System.out.printf(
            Locale.ROOT,
            "AUDIT_STRESS|matches=%d|concurrency=%d|samplesMs=%s|p50Ms=%d|maxMs=%d|throughputPerSecond=%.1f|peakAdditionalBytes=%d%n",
            matchCount,
            concurrency,
            measurement.elapsedMillis(),
            measurement.p50Millis(),
            measurement.maxMillis(),
            measurement.throughputPerSecond(),
            measurement.peakAdditionalBytes());
      }
    }

    long memoryBudgetPerRequest = Math.min(64L * 1024 * 1024, maxHeap / 20);
    int selected =
        MATCH_COUNTS.stream()
            .filter(
                count ->
                    measurements.stream()
                        .filter(value -> value.matches() == count && value.concurrency() == 8)
                        .allMatch(
                            value ->
                                value.maxMillis() * 2 < requestTimeoutMillis / 2
                                    && (value.peakAdditionalBytes() / value.concurrency()) * 2
                                        < memoryBudgetPerRequest))
            .max(Integer::compareTo)
            .orElse(0);
    System.out.printf(
        Locale.ROOT,
        "AUDIT_STRESS|selectedCeiling=%d|memoryBudgetPerRequestBytes=%d|rule=2x-below-half-timeout-and-memory%n",
        selected,
        memoryBudgetPerRequest);
  }

  private Measurement measure(
      ApiV2AuditStrictSearch search,
      ApiV2AuditStrictSearch.Request request,
      int matches,
      int concurrency)
      throws Exception {
    List<Long> samples = new ArrayList<>();
    long peakAdditional = 0;
    for (int sample = 0; sample < SAMPLES; sample++) {
      PassMeasurement first = runPass(search, request, matches, concurrency);
      PassMeasurement later = runPass(search, request, matches, concurrency);
      for (int worker = 0; worker < concurrency; worker++) {
        assertEquals(first.results().get(worker), later.results().get(worker));
      }
      samples.add(Math.max(first.elapsedMillis(), later.elapsedMillis()));
      peakAdditional =
          Math.max(
              peakAdditional, Math.max(first.peakAdditionalBytes(), later.peakAdditionalBytes()));
    }
    List<Long> sorted = samples.stream().sorted().toList();
    long maxMillis = sorted.get(sorted.size() - 1);
    double throughput =
        (double) matches * concurrency * 1000 / Math.max(1, sorted.get(sorted.size() / 2));
    return new Measurement(
        matches,
        concurrency,
        List.copyOf(samples),
        sorted.get(sorted.size() / 2),
        maxMillis,
        throughput,
        peakAdditional);
  }

  private PassMeasurement runPass(
      ApiV2AuditStrictSearch search,
      ApiV2AuditStrictSearch.Request request,
      int matches,
      int concurrency)
      throws Exception {
    System.gc();
    long before = usedHeap();
    AtomicLong peak = new AtomicLong(before);
    ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
    sampler.scheduleAtFixedRate(
        () -> peak.accumulateAndGet(usedHeap(), Math::max), 0, 10, TimeUnit.MILLISECONDS);
    ExecutorService workers = Executors.newFixedThreadPool(concurrency);
    long started = System.nanoTime();
    List<FingerprintResult> results = new ArrayList<>();
    try {
      List<Callable<FingerprintResult>> calls = new ArrayList<>();
      for (int worker = 0; worker < concurrency; worker++) {
        calls.add(() -> scanFingerprint(search, request));
      }
      for (Future<FingerprintResult> future : workers.invokeAll(calls)) {
        FingerprintResult result = future.get();
        assertEquals(matches, result.count());
        results.add(result);
      }
    } finally {
      workers.shutdownNow();
      sampler.shutdownNow();
    }
    return new PassMeasurement(
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
        Math.max(0, peak.get() - before),
        List.copyOf(results));
  }

  private static FingerprintResult scanFingerprint(
      ApiV2AuditStrictSearch search, ApiV2AuditStrictSearch.Request request) {
    List<ApiV2AuditEvent> events =
        search.search(request).stream()
            .map(ApiV2AuditSearchStressTest::event)
            .sorted(
                Comparator.comparing(ApiV2AuditEvent::timestamp)
                    .reversed()
                    .thenComparing(ApiV2AuditEvent::eventId))
            .toList();
    return new FingerprintResult(events.size(), ApiV2AuditLog.snapshotFingerprint(events));
  }

  private static ApiV2AuditEvent event(AuditTrailSearchResult result) {
    var source = result.getEvent();
    ApiV2AuditEvent withoutId =
        new ApiV2AuditEvent(
            null,
            Instant.ofEpochMilli(result.getTimestamp()).toString(),
            source.getSubject(),
            source.getFullName(),
            source.getDomain(),
            source.getAction(),
            source.getDescription(),
            source.getData().getData());
    return new ApiV2AuditEvent(
        ApiV2AuditLog.eventId(withoutId),
        withoutId.timestamp(),
        withoutId.username(),
        withoutId.fullName(),
        withoutId.domain(),
        withoutId.action(),
        withoutId.description(),
        withoutId.payload());
  }

  private void writeLogs(int matches) throws IOException {
    int perFile = (int) Math.ceil(matches / 4.0);
    int written = 0;
    for (int fileNumber = 0; fileNumber < 4; fileNumber++) {
      Path file = directory.resolve(fileNumber == 0 ? "RSLogs.txt" : "RSLogs.txt." + fileNumber);
      try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
        int inThisFile = Math.min(perFile, matches - written);
        for (int index = 0; index < inThisFile; index++) {
          int sequence = written + index;
          writer.write(line(sequence, true));
          writer.newLine();
          if (sequence % 10 == 0) {
            writer.write(line(sequence, false));
            writer.newLine();
          }
        }
        written += inThisFile;
      }
    }
  }

  private static String line(int sequence, boolean matching) {
    int hour = 1 + (sequence / 3_600) % 20;
    int minute = (sequence / 60) % 60;
    int second = sequence % 60;
    String id = matching ? "BC-STRESS" : "OTHER";
    String action = matching ? "WRITE" : "READ";
    return "%02d Jan 2026 %02d:%02d:%02d,%03d - domain:UNKNOWN action:%s [{\"data\":{\"id\":\"%s\",\"name\":\"Microscope %d\",\"description\":\"Representative audit payload for bounded snapshot measurement\",\"enabled\":true,\"timezone\":\"Europe/Berlin\"}}] audit-user(Audit Stress User)"
        .formatted(
            1 + (sequence / 72_000), hour, minute, second, sequence % 1_000, action, id, sequence);
  }

  private void clearLogs() throws IOException {
    try (var paths = Files.list(directory)) {
      for (Path path : paths.toList()) {
        Files.delete(path);
      }
    }
  }

  private static long usedHeap() {
    return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
  }

  private record FingerprintResult(int count, String fingerprint) {}

  private record PassMeasurement(
      long elapsedMillis, long peakAdditionalBytes, List<FingerprintResult> results) {}

  private record Measurement(
      int matches,
      int concurrency,
      List<Long> elapsedMillis,
      long p50Millis,
      long maxMillis,
      double throughputPerSecond,
      long peakAdditionalBytes) {}
}
