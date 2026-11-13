package com.researchspace.api.v2.throttling;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.util.throttling.ThrottleInterval;
import org.junit.jupiter.api.Test;

class APIRequestThrottlerTest {

  @Test
  void passThruProceedsAndReportsNoFabricatedStats() {
    assertTrue(APIRequestThrottler.PASS_THRU.proceed("any-key"));
    assertTrue(
        APIRequestThrottler.PASS_THRU.getStats("any-key", ThrottleInterval.QUARTER_MIN).isEmpty());
  }
}
