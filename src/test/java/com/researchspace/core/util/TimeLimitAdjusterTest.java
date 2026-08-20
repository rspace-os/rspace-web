package com.researchspace.core.util;

import static org.junit.Assert.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import lombok.Data;

public class TimeLimitAdjusterTest {
	@Data
	static class TestAdjustable implements DateRangeAdjustable {
		private Date dateFrom;
		private Date dateTo;
	}
	
	DateRangeRestrictor timeLimitadjuster;
	// 6 months can never be more than 185 days
	Duration sixMonthDuration = Duration.of(185, ChronoUnit.DAYS);
	Duration approx6MonthDuration = Duration.of(183, ChronoUnit.DAYS);
	
	Instant now = null;
	Date nowDate = null;
	Instant minusFourYears = null;
	 Instant minusTwoYears = null;

	@Before
	public void setUp() throws Exception {
		timeLimitadjuster = new DateRangeRestrictor();
		now = Instant.now();
		nowDate = new Date(now.toEpochMilli());
	    minusFourYears = now.minus(1460, ChronoUnit.DAYS);
	    minusTwoYears = now.minus(730, ChronoUnit.DAYS);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAdjustDateRangeNotSet() {
		TestAdjustable cfg = new TestAdjustable();
		timeLimitadjuster.restrictDateRange(cfg, approx6MonthDuration);
		assertNotNull(cfg.getDateFrom());
		Duration adjusted = Duration.ofMillis(nowDate.getTime() - cfg.getDateFrom().getTime());
		assertFalse(sixMonthDuration.minus(adjusted).isNegative());
		
	}
	
	@Test
	public void testAdjustDateRangeDateFromTooOldAndDateToNotSet() {
		TestAdjustable cfg = new TestAdjustable();
		Date twoYearsAgo = new Date(minusTwoYears.toEpochMilli());
		cfg.setDateFrom(twoYearsAgo);
		assertTrue(sixMonthDuration.minus(Duration.between(minusTwoYears, now)).isNegative());
		timeLimitadjuster.restrictDateRange(cfg, approx6MonthDuration);
		Date nowDate = new Date();
		assertNull(cfg.getDateTo());
		Duration adjusted = Duration.ofMillis(nowDate.getTime() - cfg.getDateFrom().getTime());
		assertFalse(sixMonthDuration.minus(adjusted).isNegative());		
	}
	
	@Test
	public void testAdjustDateRangeDateToSetOnly() {
		TestAdjustable cfg = new TestAdjustable();
		Date twoYearsAgo = new Date(minusTwoYears.toEpochMilli());
		cfg.setDateTo(twoYearsAgo);
		timeLimitadjuster.restrictDateRange(cfg, approx6MonthDuration);
		assertNotNull(cfg.getDateFrom());
		Duration adjusted = Duration.ofMillis(twoYearsAgo.getTime() - cfg.getDateFrom().getTime());
		assertFalse(sixMonthDuration.minus(adjusted).isNegative());		
	}
	
	@Test
	public void testAdjustDateRangeLimitFromDateIfBothSetTooWideRange() {
		TestAdjustable cfg = new TestAdjustable();	
		Date twoYearsAgo = new Date(minusTwoYears.toEpochMilli());
		Date fourYearsAgo = new Date(minusFourYears.toEpochMilli());
		cfg.setDateFrom(fourYearsAgo);
		cfg.setDateTo(twoYearsAgo);
		// range is > 6 months sanity check
		assertTrue(sixMonthDuration.minus(Duration.between(minusFourYears, minusTwoYears)).isNegative());
		timeLimitadjuster.restrictDateRange(cfg, approx6MonthDuration);
		Duration adjusted = Duration.ofMillis(cfg.getDateTo().getTime() - cfg.getDateFrom().getTime());
		// afterwards, it hasb een restricted.
		assertFalse(sixMonthDuration.minus(adjusted).isNegative());		
	}
	
	@Test
	public void testDateRangeNotAdjustedIfOK() {
		TestAdjustable cfg = new TestAdjustable();
		Instant minusTwoYearsAnd30days= now.minus(760, ChronoUnit.DAYS);
		Duration okrange = Duration.between(minusTwoYearsAnd30days, minusTwoYears);
		Date from = new Date(minusTwoYearsAnd30days.toEpochMilli());
		Date to = new Date(minusTwoYears.toEpochMilli());
		cfg.setDateFrom(from);
		cfg.setDateTo(to);
		timeLimitadjuster.restrictDateRange(cfg, approx6MonthDuration);
		Duration adjusted = Duration.ofMillis(cfg.getDateTo().getTime() - cfg.getDateFrom().getTime());
		// not adjusted
		assertTrue(adjusted.minus(okrange).isZero());
	}
	
	@Test
	public void testDateRangeNotAdjustedIfFromRangeIsOK() {
		TestAdjustable cfg = new TestAdjustable();
	
		Instant minus30days = now.minus(30, ChronoUnit.DAYS);
		Duration okrange = Duration.between(minus30days, now);
		Date from = new Date(minus30days.toEpochMilli());
		cfg.setDateFrom(from);
		timeLimitadjuster.restrictDateRange(cfg, approx6MonthDuration);
		Duration adjusted = Duration.ofMillis(now.toEpochMilli() - cfg.getDateFrom().getTime());
		// not adjusted
		assertTrue(adjusted.minus(okrange).isZero());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testDateRangeThrowsIAEIfToBeforeFrom() {
		TestAdjustable cfg = new TestAdjustable();
		Date fourYearsAgo = new Date(minusFourYears.toEpochMilli());
		cfg.setDateFrom(nowDate);
		cfg.setDateTo(fourYearsAgo);
		timeLimitadjuster.restrictDateRange(cfg, approx6MonthDuration);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testDateRangeThrowsIAEIfDurationis0() {
		TestAdjustable cfg = new TestAdjustable();
		timeLimitadjuster.restrictDateRange(cfg, Duration.ofMillis(0));
	}

}
