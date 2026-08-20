package com.researchspace.model.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.core.RecordType;

public class RecordDocView {

	RSpaceDocView docView;
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetGlobalIdentifier() {
		docView = new RSpaceDocView();
		docView.setType("NORMAL");
		docView.setId(23L);
		assertEquals("SD23", docView.getGlobalIdentifier());
	}

	@Test
	public void testIsMediaRecord() {
		docView = new RSpaceDocView();
	
		docView.setType(RecordType.MEDIA_FILE.name());
		assertTrue(docView.isMediaRecord());
		docView.setType(RecordType.NORMAL.name());
		assertFalse(docView.isMediaRecord());
	}

	@Test
	public void testIsStructuredDocument() {
		docView = new RSpaceDocView();
		docView.setType(RecordType.MEDIA_FILE.name());
		assertFalse(docView.isStructuredDocument());
		docView.setType(RecordType.NORMAL.name());
		assertTrue(docView.isStructuredDocument());
	}
}
