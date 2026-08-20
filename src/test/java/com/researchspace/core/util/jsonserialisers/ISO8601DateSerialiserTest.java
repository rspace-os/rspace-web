package com.researchspace.core.util.jsonserialisers;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ISO8601DateSerialiserTest {
	
	ISO8601DateSerialiser dateSerialiser;

	@Before
	public void setUp() throws Exception {
		dateSerialiser = new ISO8601DateSerialiser();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void getDateString() {
		final long TIME_IN_MILLIS = 1521590400000L;
		assertEquals("2018-03-21", dateSerialiser.getDateString(TIME_IN_MILLIS));
	}

}
