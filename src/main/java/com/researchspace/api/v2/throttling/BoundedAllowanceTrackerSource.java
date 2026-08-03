package com.researchspace.api.v2.throttling;

import com.researchspace.core.util.TimeSource;
import com.researchspace.core.util.throttling.AllowanceTracker;
import com.researchspace.core.util.throttling.AllowanceTrackerSource;
import com.researchspace.core.util.throttling.AllowanceTrackerSourceImpl;
import com.researchspace.core.util.throttling.ThrottleDefinitionSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Size-capped, least-recently-used {@link AllowanceTrackerSource}.
 *
 * <p>Deviation from v1: v1 uses {@link AllowanceTrackerSourceImpl}, whose map of trackers has no
 * expiry or size cap. Because the throttle interceptor runs before authentication and keys buckets
 * on the caller-supplied {@code apiKey}/{@code Authorization} header, unauthenticated requests each
 * carrying a distinct random header value allocate one tracker per value forever. This
 * implementation bounds that to {@link #maxEntries} entries.
 *
 * <p>Eviction resets an evicted key's allowance, so a caller who churns enough distinct keys can
 * evict and reset their own bucket. The per-user limit is therefore best-effort under attack; the
 * hard ceiling is the separate global throttler, whose source holds exactly the one {@code
 * global-id} key and so can never evict it. Resetting an allowance is the deliberate trade for not
 * running the JVM out of memory.
 *
 * <p>Every method is synchronized, which also guarantees that concurrent first requests for one key
 * receive the same {@link AllowanceTracker} instance. {@link ApiRequestThrottlerImpl} relies on
 * that identity to lock per key.
 */
public final class BoundedAllowanceTrackerSource implements AllowanceTrackerSource {

  /** Enough for every real user of a deployment, small enough to bound a flood of bogus keys. */
  public static final int DEFAULT_MAX_ENTRIES = 10_000;

  private final TimeSource timeSource;
  private final ThrottleDefinitionSet definitions;
  private final int maxEntries;
  private final Map<String, AllowanceTracker> trackers;

  public BoundedAllowanceTrackerSource(TimeSource timeSource, ThrottleDefinitionSet definitions) {
    this(timeSource, definitions, DEFAULT_MAX_ENTRIES);
  }

  public BoundedAllowanceTrackerSource(
      TimeSource timeSource, ThrottleDefinitionSet definitions, int maxEntries) {
    this.timeSource = Objects.requireNonNull(timeSource, "Time source");
    this.definitions = Objects.requireNonNull(definitions, "Throttle definitions");
    if (maxEntries < 1) {
      throw new IllegalArgumentException("Maximum tracked keys must be at least 1");
    }
    this.maxEntries = maxEntries;
    this.trackers =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, AllowanceTracker> eldest) {
            return size() > BoundedAllowanceTrackerSource.this.maxEntries;
          }
        };
  }

  @Override
  public synchronized AllowanceTracker getAllowance(String identifier) {
    AllowanceTracker existing = trackers.get(identifier);
    if (existing != null) {
      return existing;
    }
    // A throwaway delegate initialises the tracker's per-interval allowances using the library's
    // own logic, which is package-private and so cannot be called directly from here. The delegate
    // holds the single new entry and is discarded; this map is what bounds retention.
    AllowanceTracker created =
        new AllowanceTrackerSourceImpl(timeSource, definitions).getAllowance(identifier);
    trackers.put(identifier, created);
    return created;
  }

  public synchronized int size() {
    return trackers.size();
  }
}
