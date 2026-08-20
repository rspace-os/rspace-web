package com.researchspace.core.util;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for representation and calculation of date ranges
 */
public class DateRange implements Comparable<DateRange>, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Long from;
	private Long to;

	static final Pattern fromOnly = Pattern.compile("^\\d{4}\\-\\d{2}\\-\\d{2}");
	static final Pattern toOnly = Pattern.compile(",\\s*(\\d{4}\\-\\d{2}\\-\\d{2})$");

	/**
	 * Parses string in format 'yyyy-MM-dd' (from), or ',yyyy-MM-dd' (to) or
	 * 'yyyy-MM-dd,yyyy-MM-dd' (between - inclusive)
	 * 
	 * @param fromTodateString
	 * @return A date range
	 * @throws IllegalArgumentException
	 *             if input string is incorrect format
	 */
	public static DateRange parse(String fromTodateString) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

		Matcher fromMatcher = fromOnly.matcher(fromTodateString);
		Matcher toMatcher = toOnly.matcher(fromTodateString);
		Date from = null;
		if (fromMatcher.find()) {
			try {
				from = sdf.parse(fromMatcher.group());
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		Date to = null;
		if (toMatcher.find()) {
			try {
				to = sdf.parse(toMatcher.group(1));
			} catch (ParseException e) {
				// handled by IAE
				e.printStackTrace();
			}
		}
		if (from == null && to == null) {
			throw new IllegalArgumentException("could not parse [" + fromTodateString + "]. Argument"
					+ " must match one or both of " + fromOnly + ", or " + toOnly);
		}
		return new DateRange(from, to);
	}

	/**
	 * 
	 * @param from
	 *            From time in millis
	 * @param to
	 *            To Time in millis
	 * @throws IllegalArgumentException
	 *             if <code>from </code>> <code>to</code>.
	 */
	public DateRange(Long from, Long to) {
		if (from != null && to != null && from > to) {
			throw new IllegalArgumentException("From must be earlier than to");
		}
		this.from = from;
		this.to = to;
		if (from == null) {
			this.from = 0L;
		}
		if (to == null) {
			this.to = Long.MAX_VALUE;
		}

	}

	/**
	 * Null-safe constructor
	 * 
	 * @param fromDate
	 *            If null, set to 1970
	 * @param toDate
	 *            If null ,set to far future (date value of Long.MAXVALUE)
	 * @throws IllegalArgumentException
	 *             if fromDate is after toDate
	 */
	public DateRange(Date fromDate, Date toDate) {
		if (fromDate != null && toDate != null && fromDate.getTime() > toDate.getTime()) {
			throw new IllegalArgumentException("From must be earlier than to");
		}
		if (fromDate == null) {
			from = 0L;
		} else {
			from = fromDate.getTime();
		}
		if (toDate == null) {
			to = Long.MAX_VALUE;
		} else {
			to = toDate.getTime();
		}

	}

	/**
	 * Merges overlapping date ranges into as few distinct ranges as possible.
	 * 
	 * @param dateRanges
	 * @return A merged list of date ranges; the original list is unmodified
	 */
	public static List<DateRange> mergeAll(final List<DateRange> dateRanges) {
		List<DateRange> tomerge = new ArrayList<>();
		List<DateRange> rc = new ArrayList<>();
		tomerge.addAll(dateRanges);
		Collections.sort(tomerge);
		final int LAST_TO_TEST = tomerge.size() - 2;
		for (int i = 0; i < tomerge.size() - 1; i++) {
			DateRange curr = tomerge.get(i);
			DateRange next = tomerge.get(i + 1);
			if (curr.overlapsWith(next)) {
				DateRange merged = curr.merge(next);
				tomerge.set(i + 1, merged);
				if (i == LAST_TO_TEST) {
					rc.add(merged);
				}
			} else if (i <= LAST_TO_TEST) {
				rc.add(curr);
				if (i == LAST_TO_TEST) {
					rc.add(next);
				}
			}
		}
		return rc;

	}

	public Date getFromDate() {
		return new Date(from);
	}

	void setFrom(Long from) {
		this.from = from;
	}

	public Date getToDate() {
		return new Date(to);
	}

	void setTo(Long to) {
		this.to = to;
	}

	/**
	 * Contains argument date if date >= from and < to
	 * 
	 * @param date
	 * @return
	 */
	public boolean contains(Date date) {
		Long time = date.getTime();
		return from <= time && time < to;
	}

	boolean contains(Long time) {
		return from <= time && time < to;
	}

	/**
	 * Returns true if this dateRange fully contains the argument date range
	 * 
	 * @param range
	 * @return
	 */
	public boolean contains(DateRange range) {
		return from <= range.from && range.to < to;
	}

	/**
	 * Boolean test as to whether there is any overlap between this range and <code>range</code>
	 * E.g. for 2 date ranges A and B, this method returns true if: <ul>
	 * <li> A is a subrange of B
	 * <li> B is a subrange of A
	 * <li> There is any common time overlap between A and B
	 * </ul>
	 * @param range
	 * @return
	 */
	public boolean overlapsWith(DateRange range) {
		return contains(range.getFrom()) || contains(range.getTo())
				|| range.contains(this.from) || range.contains(this.to);
	}

	public Long getFrom() {
		return from;
	}

	public Long getTo() {
		return to;
	}

	/**
	 * Returns a new date range if the two ranges overlap, else returns a copy
	 * of this DateRange
	 * 
	 * @param range
	 * @return A DateRange range object
	 */
	public DateRange merge(DateRange range) {
		if (!overlapsWith(range)) {
			return new DateRange(getFromDate(), getToDate());
		} else {
			Long newfrom = from < range.getFrom() ? from : range.getFrom();
			Long newto = to > range.getTo() ? to : range.getTo();
			return new DateRange(newfrom, newto);
		}

	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((from == null) ? 0 : from.hashCode());
		result = prime * result + ((to == null) ? 0 : to.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DateRange other = (DateRange) obj;
		if (from == null) {
			if (other.from != null)
				return false;
		} else if (!from.equals(other.from))
			return false;
		if (to == null) {
			if (other.to != null)
				return false;
		} else if (!to.equals(other.to))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "DateRange [from=" + getFromDate().toString() + "(" + from + "), " + " to=" + getToDate().toString()
				+ "(" + to + ")]";
	}

	/**
	 * Ordered in ascending from date
	 */
	@Override
	public int compareTo(DateRange other) {
		int rc = from.compareTo(other.from);
		if (rc == 0) {
			rc = to.compareTo(other.to);
		}
		return rc;
	}

	public String getString(StringGenerator<DateRange> stringGenerator) {
		return stringGenerator.getString(this);
	}

}
