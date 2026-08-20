package com.researchspace.core.util;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NumberUtilsTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testStringToInt() {
		assertEquals(23, NumberUtils.stringToInt("23", 1));
		assertEquals(1, NumberUtils.stringToInt("25.3", 1));
		assertEquals(1, NumberUtils.stringToInt("abcd", 1));
	}

}
