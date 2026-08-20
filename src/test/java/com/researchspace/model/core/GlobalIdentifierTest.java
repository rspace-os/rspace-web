package com.researchspace.model.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GlobalIdentifierTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullOrEmptyThrowsIAE() {
		new GlobalIdentifier("");
		new GlobalIdentifier(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidSyntaxThrowsIAE() {
		new GlobalIdentifier("INVALID_ID");
	}

	@Test
	public void testValidSyntaxOK() {
		new GlobalIdentifier("SD12345");
		new GlobalIdentifier("SD12345v234");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testValidSyntaxThrowsIAE() {
		new GlobalIdentifier(GlobalIdPrefix.CH, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testValidSyntaxThrowsIAE2() {
		new GlobalIdentifier(null, 2L);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testValidSyntaxThrowsIAE3() {
		new GlobalIdentifier(null, null);
	}

	@Test
	public void testIsValid() {
		assertFalse(GlobalIdentifier.isValid(""));
		assertTrue(GlobalIdentifier.isValid("SD1234"));
		assertFalse(GlobalIdentifier.isValid("SD1234abc"));
		assertTrue(GlobalIdentifier.isValid("SD1234v234"));
	}

	@Test
	public void testGlobalIdConstructors() {
		GlobalIdentifier gid = new GlobalIdentifier("SD12345v23");
		assertEquals(GlobalIdPrefix.SD, gid.getPrefix());
		assertEquals(12345L, gid.getDbId().longValue());
		assertEquals(23L, gid.getVersionId().longValue());
		assertTrue(gid.hasVersionId());
		assertEquals("SD12345v23", gid.getIdString());

		GlobalIdentifier gid2 = new GlobalIdentifier(GlobalIdPrefix.SD, 12345L);
		assertFalse(gid2.hasVersionId());
		assertNotEquals(gid, gid2);
		
		GlobalIdentifier gid3 = new GlobalIdentifier(GlobalIdPrefix.SD, 12345L, 23L);
		assertEquals(gid, gid3);
		assertFalse(gid == gid3);
	}

	@Test
	public void comparingGlobalIds() {
		GlobalIdentifier gid1 = new GlobalIdentifier("SD123");	
		GlobalIdentifier gid2 = new GlobalIdentifier("SD124");
		GlobalIdentifier gid2v1 = new GlobalIdentifier("SD124v1");
		GlobalIdentifier gid2v2 = new GlobalIdentifier("SD124v2");
			
		assertTrue(gid1.compareTo(gid2) < 0);
		assertTrue(gid2.compareTo(gid2v1) > 0);
		assertTrue(gid2.compareTo(gid2v2) > 0);
		assertTrue(gid2v2.compareTo(gid1) > 0);
	}
	
}
