package com.researchspace.core.util.throttling;

import org.apache.commons.lang3.Validate;

/**
 * Defines an event/usage limit per time interval.
 */
public class ThrottleLimitDefinition {
	
	public static final int DEFAULT_LIMIT = 10;
	public static final float DEFAULT_PER_SECOND = 10f;
	private int limit = DEFAULT_LIMIT;
	private float per = DEFAULT_PER_SECOND;
	private String units = "units";
	private float limitPerRatio = -1;// unset

	/**
	 * 
	 * @param limit
	 *            number of events  or resource units allowed per timespan
	 * @param per timespace (in seconds), &gt; 0
	 * @throws IllegalArgumentException if <code>per</code>  &le; 0
	 */
	ThrottleLimitDefinition(int limit, float per, String units) {	
		super();
		Validate.isTrue(per > 0, "per cannot be 0  or less");
		this.limit = limit;
		this.per = per;
		this.units = units;
	}
	
	/**
	 * Uses default 'units' string as unit measurement.
	 * @param limit
	 * @param per
	 */
	ThrottleLimitDefinition(int limit, float per) {	
		this(limit, per, "units");		
	}

	@Override
	public String toString() {
		return "LimitPerDefinition [limit=" + limit + " " +units+" per=" + per + ", getLimitPerRatio()=" + getLimitPerRatio() + "]";
	}

	public String getUnits() {
		return units;
	}

	void setUnits(String units) {
		this.units = units;
	}

	void setLimit(int limit) {
		this.limit = limit;
		this.limitPerRatio = -1;
	}

	public float getLimitPerRatio() {
		if (limitPerRatio < 0) {
			limitPerRatio = (float) limit / per;
		}
		return limitPerRatio;
	}

	void setPer(float per) {
		assert per != 0;
		this.per = per;
		this.limitPerRatio = -1;
	}

	public int getLimit() {
		return limit;
	}

	public float getPer() {
		return per;
	}

}
