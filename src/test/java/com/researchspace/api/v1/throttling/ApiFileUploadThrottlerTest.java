package com.researchspace.api.v1.throttling;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.TimeSource;
import com.researchspace.core.util.throttling.AllowanceTrackerSourceImpl;
import com.researchspace.core.util.throttling.ThrottleDefinitionSet;
import com.researchspace.core.util.throttling.ThrottleInterval;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ApiFileUploadThrottlerTest {
  public @Rule MockitoRule rule = MockitoJUnit.rule();
  ApiFileUploadThrottlerImpl fileThrottler;
  @Mock TimeSource timesource;
  private String anyId = "any";

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testFileUploadOK() {
    // default is 1 per second, this should always pass
    int numTimeSourceRequests = 9;
    int numCalls = 10;
    int intervalMillis = 2000;
    DateTime[] sequentialTimes =
        setupSequentialDateTimeCalls(numTimeSourceRequests, intervalMillis);
    Mockito.when(timesource.now())
        .thenReturn(
            sequentialTimes[0], ArrayUtils.subarray(sequentialTimes, 1, numTimeSourceRequests));
    fileThrottler = setUpThrottler();
    IntStream.range(0, numCalls)
        .forEach(
            i -> {
              assertTrue(fileThrottler.proceed(anyId, 0.5));
            });
    APIFileUploadStats stats = getStatsForHourBucket();
    // after 10 calls over 20s, we've used up 5.0Mb of allowance, but due to recovery has added
    // 0.03Mb allowance back
    // over the 20s
    assertEquals(5.03, stats.getRemainingCapacityInPeriod(), 0.1);
  }

  private APIFileUploadStats getStatsForHourBucket() {
    Optional<APIFileUploadStats> stats = fileThrottler.getStats(anyId, ThrottleInterval.HOUR);
    return stats.get();
  }

  @Test
  public void testFileUploadExceeded() throws Exception {
    // default is 1 per second, this should always pass
    int numTimeSourceRequests = 21;
    int numCalls = 2;
    int intervalMillis = 60_000; // e.g one per minute
    DateTime[] sequentialTimes =
        setupSequentialDateTimeCalls(numTimeSourceRequests, intervalMillis);
    Mockito.when(timesource.now())
        .thenReturn(
            sequentialTimes[0], ArrayUtils.subarray(sequentialTimes, 1, numTimeSourceRequests));
    fileThrottler = setUpThrottler(); // 10Mb limit per hour
    IntStream.range(0, numCalls)
        .forEach(
            i -> {
              assertTrue(fileThrottler.proceed(anyId, 5.0));
            });
    assertExceptionThrown(
        () -> fileThrottler.proceed(anyId, 5.0), FileUploadLimitExceededException.class);
  }

  @Test
  public void testAnySingleFileCanBeUploadedEvenIfGreaterThanAllowance() throws Exception {
    // default is 1 per second, this should always pass
    int numTimeSourceRequests = 3;
    int intervalMillis = 59 * 60 * 1000; // e.g one per 59 minutes
    DateTime[] sequentialTimes =
        setupSequentialDateTimeCalls(numTimeSourceRequests, intervalMillis);
    Mockito.when(timesource.now())
        .thenReturn(
            sequentialTimes[0], ArrayUtils.subarray(sequentialTimes, 1, numTimeSourceRequests + 1));
    fileThrottler = setUpThrottler(); // 10Mb limit per hour
    assertTrue(fileThrottler.proceed(anyId, 11.0)); // single file ok
    APIFileUploadStats stats = getStatsForHourBucket();
    // ok for stats to go -ve if we're uploading 1 file
    assertTrue(stats.getRemainingCapacityInPeriod() < 0);

    // next request, we've still not recovered from previous upload
    assertExceptionThrown(
        () -> fileThrottler.proceed(anyId, 9.0), FileUploadLimitExceededException.class);
    // now, we're in the next hour, so OK
    assertTrue(fileThrottler.proceed(anyId, 10.0)); // single file ok
  }

  ApiFileUploadThrottlerImpl setUpThrottler() {
    ThrottleDefinitionSet set = createThrottleDefs();
    fileThrottler =
        new ApiFileUploadThrottlerImpl(
            timesource, set, new AllowanceTrackerSourceImpl(timesource, set));
    return fileThrottler;
  }

  private ThrottleDefinitionSet createThrottleDefs() {
    ThrottleDefinitionSet set = new ThrottleDefinitionSet("Mb");
    set.addDefinition(ThrottleInterval.HOUR, 10);
    return set;
  }

  private DateTime[] setupSequentialDateTimeCalls(int numCalls, int intervalMillis) {
    DateTime[] sequentialTimes = new DateTime[numCalls + 1];
    sequentialTimes[0] = new DateTime(0);
    IntStream.rangeClosed(1, numCalls)
        .forEach(
            i -> {
              sequentialTimes[i] = new DateTime(i * intervalMillis);
            });
    return sequentialTimes;
  }

  @Test
  public void testMultiThreadedThrotterForManyUsersRunsWithoutConcurrencyErrors() throws Exception {
    int numTimeSourceRequests = 21;
    int numCalls = 10;
    int intervalMillis = 60_000;
    final int numThreads = 50;
    List<String> apiKeys = createApiKeys(numThreads);
    List<Callable<APIFileUploadStats>> callables = new ArrayList<>();
    List<Future<APIFileUploadStats>> futures = new ArrayList<>();
    // set 50 users to make calls to simulate calls to the throttler.
    for (final String key : apiKeys) {
      DateTime[] sequentialTimes = setupSequentialDateTimeCalls(numCalls, intervalMillis);
      TimeSource timesource = mock(TimeSource.class);
      // set up repeating calls to advance time 1 minute
      when(timesource.now())
          .thenReturn(
              sequentialTimes[0],
              ArrayUtils.subarray(sequentialTimes, 1, numTimeSourceRequests + 1));
      final ThrottleDefinitionSet set = createThrottleDefs();
      final ApiFileUploadThrottlerImpl fileThrottler =
          new ApiFileUploadThrottlerImpl(
              timesource, set, new AllowanceTrackerSourceImpl(timesource, set));
      Callable<APIFileUploadStats> callable =
          () -> {
            IntStream.range(0, numCalls)
                .forEach(
                    i -> {
                      assertTrue(fileThrottler.proceed(key, 0.5));
                    });
            return fileThrottler.getStats(key, ThrottleInterval.HOUR).get();
          };
      callables.add(callable);
    }
    // set up thread pool
    ExecutorService pool = Executors.newFixedThreadPool(numThreads);
    for (Callable<APIFileUploadStats> callable : callables) {
      Future<APIFileUploadStats> future = pool.submit(callable);
      futures.add(future);
    }
    // assert all users end in the same state
    // they have all made 10 calls to use 0.5 units each.
    for (Future<APIFileUploadStats> future : futures) {
      APIFileUploadStats stat = future.get(5, TimeUnit.SECONDS);
      assertEquals(6.5, stat.getRemainingCapacityInPeriod(), 0.1);
    }
  }

  private List<String> createApiKeys(int numKeys) {
    return IntStream.range(0, numKeys).mapToObj(i -> "key-" + i).collect(Collectors.toList());
  }
}
