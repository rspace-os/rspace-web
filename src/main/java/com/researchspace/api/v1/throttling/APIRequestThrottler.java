package com.researchspace.api.v1.throttling;

import com.researchspace.core.util.throttling.ThrottleInterval;
import com.researchspace.core.util.throttling.Throttler;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import java.util.Optional;

/** Determines if a request should proceed, based on usage limits. */
public interface APIRequestThrottler extends Throttler {

  /**
   * Gets minimum interval between requests.<br>
   * Default is 0
   *
   * @return an int
   * @apiNote Returns 0 by default, i.e., there is no minimum interval unless this is overridden.
   */
  public default int getMinIntervalMillis() {
    return 0;
  }

  /**
   * The optional return value will be set (i.e., non-null) if this throttler has been configured to
   * monitor usage for the given <code>THROTTLE_INTERVAL</code>. If not, the returned Optional will
   * contain <code>null</code>.
   *
   * @param identifier
   * @param throttleInterval
   * @return An Optional<APIUsageStats>
   */
  Optional<APIUsageStats> getStats(String identifier, ThrottleInterval throttleInterval);

  /** Always returns true */
  public static final APIRequestThrottler PASS_THRU =
      new APIRequestThrottler() {

        @Override
        public Optional<APIUsageStats> getStats(
            String identifier, ThrottleInterval throttleInterval) {
          APIUsageStats ok =
              APIUsageStats.builder()
                  .periodSeconds(10)
                  .minDelayTillNextRequestMillis(100)
                  .totalRequestsPerPeriod(10)
                  .remainingRequestsInPeriod(10.0f)
                  .build();
          return Optional.of(ok);
        }

        public String getName() {
          return "passthru";
        }

        public String toString() {
          return getName();
        }
      };

  /** Rejects all requests, always throwing a {@link TooManyRequestsException} */
  public static final APIRequestThrottler ALWAYS_BLOCK =
      new APIRequestThrottler() {

        @Override
        public boolean proceed(String identifier) {
          throw new TooManyRequestsException("This implementation always throws this exception");
        }

        @Override
        public boolean proceed(String identifier, Double requestedUnits) {
          throw new TooManyRequestsException("This implementation always throws this exception");
        }

        @Override
        public Optional<APIUsageStats> getStats(
            String identifier, ThrottleInterval throttleInterval) {
          APIUsageStats allUsed =
              APIUsageStats.builder()
                  .periodSeconds(10)
                  .minDelayTillNextRequestMillis(100)
                  .totalRequestsPerPeriod(10)
                  .remainingRequestsInPeriod(0.0f)
                  .build();
          return Optional.of(allUsed);
        }

        public String getName() {
          return "alwaysBlock";
        }

        public String toString() {
          return getName();
        }
      };
}
