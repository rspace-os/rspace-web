package com.researchspace.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DateRangeTest {

	DateRange d1, d2, d3, d4, Spansd1Tod3;

	List<DateRange> setup = new ArrayList<>();
	List<DateRange> allRanges = new ArrayList<>();

	@Before
	public void setUp() throws Exception {

		allRanges = new ArrayList<>();
		d1 = new DateRange(new Date(10), new Date(20));
		d2 = new DateRange(new Date(15), new Date(25));
		d3 = new DateRange(new Date(21), new Date(30));
		d4 = new DateRange(new Date(25), new Date(35));
		Spansd1Tod3 = new DateRange(new Date(00), new Date(31));
		setup = Arrays.asList(new DateRange[] { d1, d3, d4, d2 });
		allRanges.addAll(setup);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testMergeAll() {
		List<DateRange> mergedAll = DateRange.mergeAll(allRanges);
		assertEquals(1, mergedAll.size());
		assertEquals(10, mergedAll.get(0).getFrom().intValue());
		assertEquals(35, mergedAll.get(0).getTo().intValue());

		allRanges.add(new DateRange(new Date(100), new Date(200)));
		List<DateRange> mergedAll2 = DateRange.mergeAll(allRanges);
		assertEquals(2, mergedAll2.size());
	}

	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

	@Test(expected = IllegalArgumentException.class)
	public void testParse() throws ParseException {
		DateRange between = DateRange.parse("1900-01-23,2100-01-23");
		assertTrue(between.getFromDate().after(sdf.parse("1900-01-22")));
		assertTrue(between.getToDate().before(sdf.parse("2100-01-24")));

		DateRange fromOnly = DateRange.parse("1900-01-23");
		assertTrue(fromOnly.getTo() == Long.MAX_VALUE);

		DateRange toOnly = DateRange.parse(",1900-01-23");
		assertTrue(toOnly.getFrom() == 0L);

		DateRange.parse(",1900/01/23"); // illegal format throws IAE

	}

	@Test
	public void testContainsDate() {
		assertTrue(new DateRange(new Date(0), new Date(30)).contains(d1));
		assertFalse(new DateRange(new Date(0), new Date(20)).contains(d4));
	}

	@Test
	public void testContainsDateRange() {
		assertFalse(d1.contains(d2));
		assertTrue(Spansd1Tod3.contains(d1));
	}

	@Test
	public void testOverlapsWith() {	
		assertTrue(Spansd1Tod3.overlapsWith(d1));
		assertTrue(d1.overlapsWith(Spansd1Tod3));
		assertTrue(d1.overlapsWith(d2));
		assertFalse(d1.overlapsWith(d3));
	}

	@Test
	public void testMerge() {
		assertEquals(d1, d1.merge(d4));// no overlap
		assertEquals(new DateRange(new Date(10), new Date(25)), d1.merge(d2));// overlap
	}

	@Test
	public void testEqualsObject() {
		assertEquals(d1, getCopy(d1));
	}

	@Test
	public void testCompareToConsistentWithEquals() {
		assertEquals(0, d1.compareTo(getCopy(d1)));
	}

	DateRange getCopy(DateRange d) {
		return new DateRange(d.getFromDate(), d.getToDate());
	}

}
