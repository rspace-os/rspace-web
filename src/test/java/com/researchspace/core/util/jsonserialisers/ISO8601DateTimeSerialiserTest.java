package com.researchspace.core.util.jsonserialisers;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ISO8601DateTimeSerialiserTest {
	
	ISO8601DateTimeSerialiser dateSerialiser;

	@Before
	public void setUp() throws Exception {
		dateSerialiser = new ISO8601DateTimeSerialiser();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void getDateString() {
		final long TIME_IN_MILLIS = 1521590400000L;
		assertEquals("2018-03-21T00:00:00.000Z", dateSerialiser.getDateTimeString(TIME_IN_MILLIS));
	}

}
