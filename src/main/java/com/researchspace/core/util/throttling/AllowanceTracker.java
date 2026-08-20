package com.researchspace.core.util.throttling;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joda.time.DateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Stores allowances of a single API client.
 */
@Data
@ToString(of = { "lastCheck", "firstTime", "userId" })
@EqualsAndHashCode(of = "userId")
public class AllowanceTracker {

	private DateTime lastCheck;
	private Map<ThrottleInterval, Double> allowances = new ConcurrentHashMap<>();
	private boolean firstTime = true;
	private String userId;

	void initialiseAllowance(ThrottleInterval throttleInterval, double rate) {
		allowances.putIfAbsent(throttleInterval, rate);
	}

	/**
	 * Decrements and stores the new allowance.
	 * 
	 * @return the new allowance
	 */
	double decrementAllowance(ThrottleInterval interval, double currAllowance, double amountToDecrement) {
		allowances.put(interval, currAllowance - amountToDecrement);
		return currAllowance - amountToDecrement;
	}

	public double getAllowance(ThrottleInterval interval) {
		return allowances.get(interval);
	}

	public AllowanceTracker(String userId2) {
		this.userId = userId2;
	}

}
