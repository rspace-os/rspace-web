package com.researchspace.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;

public class GlobalIdentifierTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetRoundTrip() {
		GlobalIdentifier gid =	new GlobalIdentifier(GlobalIdPrefix.FD+"12345");
		GlobalIdentifier gid2 =	new GlobalIdentifier(GlobalIdPrefix.FD, 12345L);
		assertEquals(gid,gid2);
		GlobalIdentifier gid3 =	new GlobalIdentifier(gid2.getIdString());
		assertEquals(gid,gid3);
		GlobalIdentifier gid4 =	new GlobalIdentifier(gid.getIdString());
		assertEquals(gid3,gid4);
		
	}

	@Test
	(expected=IllegalArgumentException.class)
	public void testGlobalIdentifierNotNullString() {
		assertFalse(GlobalIdentifier.isValid(null));
		new GlobalIdentifier(null);
	}
	
	@Test
	(expected=IllegalArgumentException.class)
	public void testGlobalIdentifierStringNotEmpty() {
		assertFalse(GlobalIdentifier.isValid(""));
		new GlobalIdentifier("");
	}
	
	@Test
	(expected=IllegalArgumentException.class)
	public void testGlobalIdentifierStringWrongSyntax() {
		assertFalse(GlobalIdentifier.isValid("12345"));
		new GlobalIdentifier("12345");
	}
	
	@Test
	(expected=IllegalArgumentException.class)
	public void testGlobalIdentifierStringUnknownPrefix() {
		assertFalse(GlobalIdentifier.isValid("XX12345"));
		new GlobalIdentifier("XX12345");
	}

	@Test
	public void testGetPrefix() {
		GlobalIdentifier gid =	new GlobalIdentifier(GlobalIdPrefix.FD+"12345");
		assertTrue(GlobalIdentifier.isValid(GlobalIdPrefix.FD+"12345"));
		assertEquals(GlobalIdPrefix.FD, gid.getPrefix());
		assertEquals(12345, gid.getDbId().intValue());
	}



}
