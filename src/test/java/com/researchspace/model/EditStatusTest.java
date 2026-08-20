package com.researchspace.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EditStatusTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testIsEditable() {
		assertTrue(EditStatus.EDIT_MODE.isEditable());
		assertFalse(EditStatus.CANNOT_EDIT_OTHER_EDITING.isEditable());
		assertFalse(EditStatus.CANNOT_EDIT_NO_PERMISSION.isEditable());
		assertFalse(EditStatus.CAN_NEVER_EDIT.isEditable());
	}

}
