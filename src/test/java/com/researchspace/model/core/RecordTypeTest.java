package com.researchspace.model.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RecordTypeTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAllTypesReturnAPrefix() {
		EnumSet<RecordType> allTypes = EnumSet.allOf(RecordType.class);
		for (RecordType type : allTypes) {
			assertNotNull(RecordType.getGlobalIdFromType(type.name()));
		}
	}

	@Test
	public void testAllTypesInConcatenatedStringReturnAPrefix() {
		EnumSet<RecordType> allTypes = EnumSet.allOf(RecordType.class);
		String allTypesJoined = StringUtils.join(allTypes, ":");
		// this is just returned first
		assertEquals(GlobalIdPrefix.ST, RecordType.getGlobalIdFromType(allTypesJoined));
	}
	
	@Test
	public void testPrecedenceRules() {
		assertEquals(GlobalIdPrefix.NB, RecordType.getGlobalIdFromType("NOTEBOOK:FOLDER"));
		assertEquals(GlobalIdPrefix.GL, RecordType.getGlobalIdFromType("MEDIA_FILE:NORMAL"));
		assertEquals(GlobalIdPrefix.SD, RecordType.getGlobalIdFromType("TEMPLATE"));
		assertEquals(GlobalIdPrefix.FL, RecordType.getGlobalIdFromType("FOLDER:SYSTEM:TEMPLATE"));
		assertEquals(GlobalIdPrefix.ST, RecordType.getGlobalIdFromType(RecordType.SNIPPET.name()));
	}
	
	@Test
	public void testSnippet() {
		assertTrue(RecordType.isSnippet("SNIPPET"));
		assertTrue(RecordType.isSnippet(RecordType.SNIPPET.name()));
	}
	
	@Test
	public void testFolderNotebookRules() {
		assertTrue(RecordType.isFolder("FOLDER"));
		assertTrue(RecordType.isFolder("NOTEBOOK"));
		assertFalse(RecordType.isNotebook("FOLDER"));
		assertTrue(RecordType.isNotebook("NOTEBOOK"));
	}

}
