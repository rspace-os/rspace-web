package com.researchspace.core.util.throttling;

import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.joda.time.DateTime;

import com.researchspace.core.util.TimeSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of a Bucket Throttler that subclasses can customise.
 * See http://stackoverflow.com/questions/667508/whats-a-good-rate-limiting-algorithm
 */
@Slf4j
public abstract class AbstractTokenBucketThrottler  {
	
	protected TimeSource timeSource = null;
	protected ThrottleDefinitionSet throttleLimitDefinitions;
	protected AllowanceTrackerSource allowanceTrackerSource;
	
	/**
	 * All args required
	 * 
	 * @param timeSource
	 * @param throttleLimitDefinitions
	 * @param allowanceSource
	 * @throws IllegalArgumentException
	 *             if any argument is null or
	 *             <code>throttleRateDefinitions</code> is empty
	 */
	public AbstractTokenBucketThrottler(TimeSource timeSource, ThrottleDefinitionSet throttleLimitDefinitions,
			AllowanceTrackerSource allowanceSource) {
		super();
		Validate.notNull(timeSource, "Time source cannot be null");
		Validate.notNull(throttleLimitDefinitions, "ThrottleDefinitionSet cannot be null");
		Validate.notNull(allowanceSource, "Allowance source cannot be null");
		Validate.isTrue(throttleLimitDefinitions.getDefinitionCount() > 0,
				"ThrottleDefinitionSet must have >= 1 throttle definition set provided");
		this.timeSource = timeSource;
		this.throttleLimitDefinitions = throttleLimitDefinitions;
		this.allowanceTrackerSource = allowanceSource;
	}
	/**
	 * Template method for iterating over throttle limits and determinging if any throttle limits have been exceeded.
	 */
	protected final void doProceed(AllowanceTracker userTracker, DateTime current, long interval, Double requestedResourceUnits) {
		for (Map.Entry<ThrottleInterval, ThrottleLimitDefinition> pair : throttleLimitDefinitions.getThrottleLimits()
				.entrySet()) {
			log.trace("interval (millis) between requests is: {} ms", interval);
			userTracker.setLastCheck(current);
			double allowance = calculateAllowance(current, interval, userTracker.getAllowance(pair.getKey()),
					pair.getValue());
			userTracker.getAllowances().put(pair.getKey(), allowance);
			log.debug("allowance for  user {} for throttle definition {} is {} {}", userTracker.getUserId(), pair.getKey(),
					String.format("%.4f", userTracker.getAllowance(pair.getKey())), pair.getValue().getUnits());

			if (!isAllowed(requestedResourceUnits, allowance, pair.getValue())) {
				String msg = getThrottleLimitExceededMessage(pair);
				log.warn(msg);
				throwThrottleException(msg);
			} else {
				userTracker.decrementAllowance(pair.getKey(), allowance, requestedResourceUnits);
			}
		}
	}

	/**
	 * Throw subclass-specfic subtype of {@link ThrottlingException}
	 * @param msg
	 */
	protected abstract void throwThrottleException(String msg) ;
	/**
	 * Calculates if allowance is large enough to handle requestedResourceUnits. Subclasses may override.
	 * @param requestedResourceUnits
	 * @param allowance
	 * @param value
	 * @return <code>true</code> if <code>requestedResourceUnits</code> can be handled.
	 */
	protected boolean isAllowed(Double requestedResourceUnits, double allowance, ThrottleLimitDefinition value) {
		return allowance - requestedResourceUnits > 0;
	}

	/**
     * Get human-readable error message if the throttle limit for has been exceeded.
     * @param pair The ThrottleLimitDefinition that has been exceeded.
     * @return
     */
	protected abstract String getThrottleLimitExceededMessage(Map.Entry<ThrottleInterval, ThrottleLimitDefinition> pair);

	private double calculateAllowance(DateTime current, long interval, double allowance, ThrottleLimitDefinition ratePer) {
		float time_passed = ((float) interval / 1000);
		allowance += time_passed * (ratePer.getLimitPerRatio());
		if (allowance > ratePer.getLimit()) {
			allowance = ratePer.getLimit(); // throttle
		}
		return allowance;
	}
	protected AllowanceTracker getAllowanceTrackerById(String id) {
		return allowanceTrackerSource.getAllowance(id);
	}

	
	protected long getIntervalSinceLastRequest(DateTime lastCheck, DateTime current) {
		return current.getMillis() - lastCheck.getMillis();
	}

}
