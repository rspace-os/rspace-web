package com.researchspace.api.v1.throttling;

import static com.researchspace.api.v1.throttling.ApiRequestThrottlerImpl.DEFAULT_MIN_INTERVAL_MILLIS;
import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.TimeSource;
import com.researchspace.core.util.throttling.AllowanceTrackerSourceImpl;
import com.researchspace.core.util.throttling.ThrottleDefinitionSet;
import com.researchspace.core.util.throttling.ThrottleInterval;
import com.researchspace.core.util.throttling.TooManyRequestsException;
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

public class ApiRequestThrottlerTest {
  public @Rule MockitoRule rule = MockitoJUnit.rule();
  ApiRequestThrottlerImpl throttler;
  @Mock TimeSource timesource;
  private String anyId = "any";

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testRequestReceivedEvery2SecondsMaintainsAllowance() {
    // default is 1 per second, this should always pass
    int numTimeSourceRequests = 21;
    int numCalls = 20;
    int intervalMillis = 2000;
    DateTime[] sequentialTimes =
        setupSequentialDateTimeCalls(numTimeSourceRequests, intervalMillis);
    Mockito.when(timesource.now())
        .thenReturn(
            sequentialTimes[0], ArrayUtils.subarray(sequentialTimes, 1, numTimeSourceRequests));
    // 10 every rolling 15 seconds allowed, we are doing 10 every 20 seconds so OK
    throttler = setUpThrottler();
    IntStream.range(0, numCalls)
        .forEach(
            i -> {
              assertTrue(throttler.proceed(anyId));
            });
  }

  ApiRequestThrottlerImpl setUpThrottler() {
    ThrottleDefinitionSet set = createThrottleDefinitionSet();
    set.addDefinition(ThrottleInterval.QUARTER_MIN, 10);
    throttler =
        new ApiRequestThrottlerImpl(
            timesource, set, new AllowanceTrackerSourceImpl(timesource, set));
    return throttler;
  }

  @Test
  public void testMultiplThrottleDefinitions() throws Exception {
    ThrottleDefinitionSet set = createThrottleDefinitionSet();
    set.addDefinition(ThrottleInterval.QUARTER_MIN, 15);
    set.addDefinition(ThrottleInterval.HOUR, 10);
    // make a request once per minute, this should just trigger the 'Hourly'
    // throttle
    int intervalMillis = 60_000;
    int numTimeSourceRequests = 13;
    int numCalls = 11;
    DateTime[] sequentialTimes =
        setupSequentialDateTimeCalls(numTimeSourceRequests, intervalMillis);
    Mockito.when(timesource.now())
        .thenReturn(
            sequentialTimes[0], ArrayUtils.subarray(sequentialTimes, 1, numTimeSourceRequests));
    throttler =
        new ApiRequestThrottlerImpl(
            timesource, set, new AllowanceTrackerSourceImpl(timesource, set));
    IntStream.range(0, numCalls)
        .forEach(
            i -> {
              assertTrue(throttler.proceed(anyId));
            });
    assertExceptionThrown(() -> throttler.proceed(anyId), TooManyRequestsException.class);
  }

  private ThrottleDefinitionSet createThrottleDefinitionSet() {
    return new ThrottleDefinitionSet("requests");
  }

  @Test
  public void minIntervalAccepts0() throws Exception {
    throttler = setUpThrottler();
    throttler.setMinIntervalMillis(0);
    assertIllegalArgumentException(() -> throttler.setMinIntervalMillis(-1));
  }

  @Test
  public void minInterval0() throws Exception {
    int maxIn15s = 9; // this is max we can make in 15, but we can make this 9 as fast as possible
    DateTime[] sequentialTimes = setupSequentialDateTimeCalls(maxIn15s, 0);
    Mockito.when(timesource.now())
        .thenReturn(sequentialTimes[0], ArrayUtils.subarray(sequentialTimes, 1, maxIn15s));
    throttler = setUpThrottler();
    throttler.setMinIntervalMillis(0);
    IntStream.range(0, maxIn15s)
        .forEach(
            i -> {
              assertTrue(throttler.proceed(anyId));
            });
  }

  @Test
  public void testMinInterval() throws Exception {
    int numTimeSourceRequests = 3;
    int TOO_SHORTintervalMillis = DEFAULT_MIN_INTERVAL_MILLIS - 1;
    DateTime[] sequentialTimes =
        setupSequentialDateTimeCalls(numTimeSourceRequests, TOO_SHORTintervalMillis);
    Mockito.when(timesource.now())
        .thenReturn(
            sequentialTimes[0], ArrayUtils.subarray(sequentialTimes, 1, numTimeSourceRequests));
    throttler = setUpThrottler();
    // first call always OK
    throttler.proceed(anyId);
    // 1 more invocation will throw exception
    assertExceptionThrown(() -> throttler.proceed(anyId), TooManyRequestsException.class);
  }

  @Test
  public void testRequestReceivedTooFrequently() throws Exception {
    // default throttling is 10/ 10 seconds
    int numTimeSourceRequests = 13;
    int numThrottlerCalls = 12;
    // 1st invocation of timesource is an initialisation
    int expectedSuccessFullInvocations = numThrottlerCalls - 1;
    int intervalMillis = 200;
    DateTime[] sequentialTimes =
        setupSequentialDateTimeCalls(numTimeSourceRequests, intervalMillis);
    Mockito.when(timesource.now())
        .thenReturn(
            sequentialTimes[0], ArrayUtils.subarray(sequentialTimes, 1, numTimeSourceRequests));
    throttler = setUpThrottler();
    IntStream.range(0, expectedSuccessFullInvocations)
        .forEach(
            i -> {
              assertTrue(throttler.proceed(anyId));
            });
    // 1 more invocation will throw exception
    assertExceptionThrown(() -> throttler.proceed(anyId), TooManyRequestsException.class);

    // but other user works fine
    Mockito.clearInvocations(timesource);
    String otherUser = "other";
    // reset invocations for other user
    Mockito.when(timesource.now())
        .thenReturn(
            sequentialTimes[0], ArrayUtils.subarray(sequentialTimes, 1, numTimeSourceRequests));
    IntStream.range(0, expectedSuccessFullInvocations)
        .forEach(
            i -> {
              assertTrue(throttler.proceed(otherUser));
            });
  }

  // this tests shows the throttler does not wait until the time window has expired.
  // e.g if you use 10 requests in 10millis, you can make new requests after max 1.5sec (the
  // recovery rate / request)
  // , you don't have to wait 14.9s for the 12 bucket to refresh.
  @Test
  public void testRequestsContinueAfterLimitExceededAfterDelay() throws Exception {

    // 1st invocation of timesource is an initialisation
    int expectedSuccessFullInvocations = 12;

    DateTime[] sequentialTimes = hitLimitPauseThenContinue();
    Mockito.when(timesource.now())
        .thenReturn(sequentialTimes[0], ArrayUtils.subarray(sequentialTimes, 1, 13));
    throttler = setUpThrottler();
    throttler.setMinIntervalMillis(1); // just so as not to have to worry about this
    IntStream.range(0, expectedSuccessFullInvocations)
        .forEach(
            i -> {
              assertTrue(throttler.proceed(anyId));
            });
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

  // assuming 10/ 15 seconds allowance
  private DateTime[] hitLimitPauseThenContinue() {
    DateTime[] sequentialTimes = new DateTime[13];
    sequentialTimes[0] = new DateTime(0);
    // make 10 very quick calls which uses up all our allowance
    IntStream.rangeClosed(1, 10)
        .forEach(
            i -> {
              sequentialTimes[i] = new DateTime(i * 20);
            });
    // we can have a longterm average of 1 request / 1.5 seconds, so now we space out at just over
    // this
    // so these should all pass

    sequentialTimes[11] = sequentialTimes[9].plus(1600);
    sequentialTimes[12] = sequentialTimes[9].plus(3200);
    return sequentialTimes;
  }
}
