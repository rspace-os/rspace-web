package com.researchspace.core.util.throttling;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.Validate;

/** A collection of throttle definitions */
public class ThrottleDefinitionSet {

  private String units = "units";

  public ThrottleDefinitionSet(String units) {
    super();
    this.units = units;
  }

  public ThrottleDefinitionSet() {}

  private Map<ThrottleInterval, ThrottleLimitDefinition> throttleRates = new LinkedHashMap<>();

  /**
   * Returns an unmodifiable map of throttle rates
   *
   * @return
   */
  Map<ThrottleInterval, ThrottleLimitDefinition> getThrottleLimits() {
    return Collections.unmodifiableMap(throttleRates);
  }

  public ThrottleDefinitionSet addDefinition(ThrottleInterval throttleInterval, int limit) {
    Validate.isTrue(limit > 0, String.format("Limit must be > 0 but was %d", limit));
    throttleRates.put(
        throttleInterval, new ThrottleLimitDefinition(limit, throttleInterval.getSeconds(), units));
    return this;
  }

  public ThrottleLimitDefinition getThrottleDefinition(ThrottleInterval throttleInterval) {
    return throttleRates.get(throttleInterval);
  }

  public int getDefinitionCount() {
    return throttleRates.size();
  }
}
