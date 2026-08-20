package com.researchspace.core.util.throttling;

/**
 * Source of AllowanceTracker keyed by an id
 */
public interface AllowanceTrackerSource {
	/**
	 * Should get or create an {@link AllowanceTracker}
	 * 
	 * @param id
	 *            A non-empty ID
	 * @return
	 */
	AllowanceTracker getAllowance(String id);

}
