package com.researchspace.api.v2.throttling;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.researchspace.core.util.TimeSource;
import com.researchspace.core.util.throttling.ThrottleDefinitionSet;
import com.researchspace.core.util.throttling.ThrottleInterval;
import com.researchspace.core.util.throttling.ThrottleLimitDefinition;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.VerboseResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** REST API request throttling backed by thread-safe Bucket4j token buckets. */
public final class Bucket4jApiRequestThrottler implements APIRequestThrottler {

  public static final int DEFAULT_MAX_TRACKED_IDENTIFIERS = 10_000;

  private static final String RATE_LIMIT_EXCEEDED = "API request rate limit exceeded";

  private final ThrottleDefinitionSet definitions;
  private final int minIntervalMillis;
  private final List<Bandwidth> bandwidths;
  private final Map<ThrottleInterval, Integer> bandwidthIndexes;
  private final TimeMeter timeMeter;
  private final Cache<String, Bucket> buckets;

  public Bucket4jApiRequestThrottler(
      TimeSource timeSource, ThrottleDefinitionSet definitions, int minIntervalMillis) {
    this(
        timeSource,
        definitions,
        minIntervalMillis,
        Bucket4jApiRequestThrottler.DEFAULT_MAX_TRACKED_IDENTIFIERS);
  }

  Bucket4jApiRequestThrottler(
      TimeSource timeSource,
      ThrottleDefinitionSet definitions,
      int minIntervalMillis,
      int maxTrackedIdentifiers) {
    Objects.requireNonNull(timeSource, "Time source");
    this.definitions = Objects.requireNonNull(definitions, "Throttle definitions");
    if (minIntervalMillis < 0) {
      throw new IllegalArgumentException("Minimum request interval must not be negative");
    }
    if (maxTrackedIdentifiers < 1) {
      throw new IllegalArgumentException("Maximum tracked identifiers must be at least 1");
    }
    this.minIntervalMillis = minIntervalMillis;
    this.timeMeter = timeMeter(timeSource);
    this.bandwidthIndexes = new EnumMap<>(ThrottleInterval.class);
    this.bandwidths = bandwidths(definitions, minIntervalMillis, bandwidthIndexes);
    this.buckets = CacheBuilder.newBuilder().maximumSize(maxTrackedIdentifiers).build();
  }

  @Override
  public boolean proceed(String identifier) {
    return proceed(identifier, 1.0);
  }

  @Override
  public boolean proceed(String identifier, Double requestedResourceUnits) {
    long tokens = wholeTokens(requestedResourceUnits);
    if (!bucket(identifier).tryConsume(tokens)) {
      throw new TooManyRequestsException(RATE_LIMIT_EXCEEDED);
    }
    return true;
  }

  @Override
  public Optional<APIUsageStats> getStats(String identifier, ThrottleInterval throttleInterval) {
    ThrottleLimitDefinition definition = definitions.getThrottleDefinition(throttleInterval);
    Integer bandwidthIndex = bandwidthIndexes.get(throttleInterval);
    if (definition == null || bandwidthIndex == null) {
      return Optional.empty();
    }

    VerboseResult<Long> snapshot = bucket(identifier).asVerbose().getAvailableTokens();
    long remaining = snapshot.getDiagnostics().getAvailableTokensPerEachBandwidth()[bandwidthIndex];
    return Optional.of(
        APIUsageStats.builder()
            .minDelayTillNextRequestMillis(minIntervalMillis)
            .periodSeconds((int) definition.getPer())
            .remainingRequestsInPeriod(remaining)
            .totalRequestsPerPeriod(definition.getLimit())
            .build());
  }

  @Override
  public int getMinIntervalMillis() {
    return minIntervalMillis;
  }

  @Override
  public String getName() {
    return "Bucket4j";
  }

  @Override
  public String toString() {
    return getName();
  }

  long trackedIdentifierCount() {
    buckets.cleanUp();
    return buckets.size();
  }

  private Bucket bucket(String identifier) {
    Objects.requireNonNull(identifier, "Throttle identifier");
    return buckets.asMap().computeIfAbsent(identifier, ignored -> newBucket());
  }

  private Bucket newBucket() {
    var builder = Bucket.builder().withCustomTimePrecision(timeMeter);
    bandwidths.forEach(builder::addLimit);
    return builder.build();
  }

  private static List<Bandwidth> bandwidths(
      ThrottleDefinitionSet definitions,
      int minIntervalMillis,
      Map<ThrottleInterval, Integer> indexes) {
    List<Bandwidth> result = new ArrayList<>();
    for (ThrottleInterval interval : ThrottleInterval.values()) {
      ThrottleLimitDefinition definition = definitions.getThrottleDefinition(interval);
      if (definition == null) {
        continue;
      }
      indexes.put(interval, result.size());
      result.add(
          Bandwidth.builder()
              .capacity(definition.getLimit())
              .refillGreedy(definition.getLimit(), period(definition))
              .id(interval.name())
              .build());
    }
    if (minIntervalMillis > 0) {
      result.add(
          Bandwidth.builder()
              .capacity(1)
              .refillGreedy(1, Duration.ofMillis(minIntervalMillis))
              .id("minimum-interval")
              .build());
    }
    if (result.isEmpty()) {
      throw new IllegalArgumentException("At least one throttle limit must be configured");
    }
    return List.copyOf(result);
  }

  private static Duration period(ThrottleLimitDefinition definition) {
    return Duration.ofMillis(Math.round(definition.getPer() * 1000));
  }

  private static long wholeTokens(Double requestedResourceUnits) {
    if (requestedResourceUnits == null
        || !Double.isFinite(requestedResourceUnits)
        || requestedResourceUnits <= 0
        || requestedResourceUnits != Math.rint(requestedResourceUnits)
        || requestedResourceUnits > Long.MAX_VALUE) {
      throw new IllegalArgumentException(
          "Requested resource units must be a positive whole number");
    }
    return requestedResourceUnits.longValue();
  }

  private static TimeMeter timeMeter(TimeSource timeSource) {
    return new TimeMeter() {
      @Override
      public long currentTimeNanos() {
        return TimeUnit.MILLISECONDS.toNanos(timeSource.now().getMillis());
      }

      @Override
      public boolean isWallClockBased() {
        return true;
      }
    };
  }
}
