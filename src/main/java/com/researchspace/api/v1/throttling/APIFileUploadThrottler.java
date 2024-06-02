package com.researchspace.api.v1.throttling;

import com.researchspace.core.util.throttling.ThrottleInterval;
import com.researchspace.core.util.throttling.Throttler;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import java.util.Optional;

/** Determines if a request should proceed, based on usage limits. */
public interface APIFileUploadThrottler extends Throttler {

  /**
   * The optional return value will be set (i.e., non-null) if this throttler has been configured to
   * monitor usage for the given <code>THROTTLE_INTERVAL</code>. If not, the returned Optional will
   * contain <code>null</code>.
   *
   * @param identifier
   * @param throttleInterval
   * @return An Optional<APIUsageStats>
   */
  Optional<APIFileUploadStats> getStats(String identifier, ThrottleInterval throttleInterval);

  /** Always returns true and does not restrict upload */
  public static final APIFileUploadThrottler PASS_THRU =
      new APIFileUploadThrottler() {

        @Override
        public Optional<APIFileUploadStats> getStats(
            String identifier, ThrottleInterval throttleInterval) {
          return Optional.of(new APIFileUploadStats(0, Integer.MAX_VALUE));
        }

        public String getName() {
          return "passthru-fileupload";
        }

        public String toString() {
          return getName();
        }
      };

  /** Rejects all requests, always throwing a {@link TooManyRequestsException} */
  public static final APIFileUploadThrottler ALWAYS_BLOCK =
      new APIFileUploadThrottler() {

        @Override
        public boolean proceed(String identifier) {
          throw new TooManyRequestsException("Too much file content ");
        }

        @Override
        public boolean proceed(String identifier, Double requestedUnits) {
          throw new TooManyRequestsException("Too much file content ");
        }

        @Override
        public Optional<APIFileUploadStats> getStats(
            String identifier, ThrottleInterval throttleInterval) {
          return Optional.of(new APIFileUploadStats(100, 0));
        }

        public String getName() {
          return "alwaysBlockFileUpload";
        }

        public String toString() {
          return getName();
        }
      };
}
