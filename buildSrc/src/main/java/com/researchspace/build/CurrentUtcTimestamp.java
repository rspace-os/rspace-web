package com.researchspace.build;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

/**
 * The current UTC time as {@code yyyyMMdd'T'HHmmss'Z'}, Maven's build-timestamp format.
 *
 * <p>Gradle re-evaluates a value source on every build to decide whether a stored configuration
 * cache entry is still valid, so a build that reads it at configuration time gets a fresh value
 * each run, where a plain {@code ZonedDateTime.now()} call would be frozen in the cache entry.
 */
public abstract class CurrentUtcTimestamp
    implements ValueSource<String, ValueSourceParameters.None> {

  public static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

  @Override
  public String obtain() {
    return FORMAT.format(ZonedDateTime.now(ZoneOffset.UTC));
  }
}
