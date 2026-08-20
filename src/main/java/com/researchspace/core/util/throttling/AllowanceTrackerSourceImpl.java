package com.researchspace.core.util.throttling;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.Validate;

import com.researchspace.core.util.TimeSource;

/**
 * Stores the allowances for a given id.
 * <p>
 * This implementation stores allowances in memory.
 */
public class AllowanceTrackerSourceImpl implements AllowanceTrackerSource {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8739614831779086577L;
	private Map<String, AllowanceTracker> userAllowances = new ConcurrentHashMap<>();
	private TimeSource timeSource;
	private ThrottleDefinitionSet throttleDefinitions;

	public AllowanceTrackerSourceImpl(TimeSource timeSource, ThrottleDefinitionSet throttleDefinitions) {
		this.timeSource = timeSource;
		this.throttleDefinitions = throttleDefinitions;
	}

	public AllowanceTracker getAllowance(String id) {
		Validate.notEmpty(id, "Id cannot be empty");
		AllowanceTracker rt = userAllowances.get(id);
		if (rt == null) {
			rt = initTracker(id);
		}
		return rt;
	}

	private AllowanceTracker initTracker(String userId) {
		AllowanceTracker rt;
		rt = new AllowanceTracker(userId);
		rt.setLastCheck(timeSource.now());
		AllowanceTracker currValue = userAllowances.putIfAbsent(userId, rt);
		if(currValue != null) {
			rt = currValue;
		}
		for (Map.Entry<ThrottleInterval, ThrottleLimitDefinition> pair : throttleDefinitions.getThrottleLimits()
				.entrySet()) {
			rt.initialiseAllowance(pair.getKey(), pair.getValue().getLimit());
		}
		return rt;
	}
}
