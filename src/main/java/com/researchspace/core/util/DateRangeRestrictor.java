package com.researchspace.core.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.apache.commons.lang3.Validate;

public class DateRangeRestrictor {

	/**
	 * Adjust a date range in the given <code>dateRangeAdjustable</code> to be
	 * no loner than the specified {@link Duration}
	 * 
	 * @param dateRangeAdjustable
	 *            The to/from end dates are optional and can be
	 *            <code>null</code>
	 * @param maxRange, non-null, must be a +ve duration
	 * @throws IllegalArgumentException if either argument <code>null</code> or <code>maxRange</code> is negative or 0
	 */
	public void restrictDateRange(DateRangeAdjustable dateRangeAdjustable, Duration maxRange) {
		Validate.notNull(dateRangeAdjustable, "Must supply a DateRangeAdjustable");
		Validate.notNull(maxRange, "Must supply a Duration");
		Validate.isTrue(!maxRange.isNegative() && !maxRange.isZero(), "Must supply a positive duration");

		Instant now = Instant.now();
		Instant earliestFromInst = now.minus(maxRange);
		Date earliestFrom = new Date(earliestFromInst.toEpochMilli());
		// no date range, restrict to maxRange
		if (dateRangeAdjustable.getDateFrom() == null && dateRangeAdjustable.getDateTo() == null) {
			dateRangeAdjustable.setDateFrom(earliestFrom);
			// date from is too long ago,
		} else if (dateRangeAdjustable.getDateFrom() != null && dateRangeAdjustable.getDateTo() == null) {
			if (dateRangeAdjustable.getDateFrom().before(earliestFrom)) {
				dateRangeAdjustable.setDateFrom(earliestFrom);
			}
			// dateto is set, datefrom limited
		} else if (dateRangeAdjustable.getDateFrom() == null && dateRangeAdjustable.getDateTo() != null) {
			Instant toInstant = Instant.ofEpochMilli(dateRangeAdjustable.getDateTo().getTime());
			Instant earlyLimit = toInstant.minus(maxRange);
			dateRangeAdjustable.setDateFrom(new Date(earlyLimit.toEpochMilli()));
			// both are set, but range is too big. Limit
		} else if (dateRangeAdjustable.getDateFrom() != null && dateRangeAdjustable.getDateTo() != null) {
			Instant toInstant = Instant.ofEpochMilli(dateRangeAdjustable.getDateTo().getTime());
			Instant fromInstant = Instant.ofEpochMilli(dateRangeAdjustable.getDateFrom().getTime());
			Duration range = Duration.between(fromInstant, toInstant);
			if (range.isNegative()) {
				throw new IllegalArgumentException(
						String.format("Time range cannot be negative but was to: %s, from :%s",
								dateRangeAdjustable.getDateTo(), dateRangeAdjustable.getDateFrom()));
			}
			if (range.toDays() > maxRange.toDays()) {
				Instant earlyLimit = toInstant.minus(maxRange);
				dateRangeAdjustable.setDateFrom(new Date(earlyLimit.toEpochMilli()));
			}
		}
	}

}
