package com.researchspace.model.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TextFieldTest {

	private static final String RTFDATA = "RTFDATA";
	 static final String DEFAULT = "default";

	@Before
	public void setUp() throws Exception {
		tf = FieldTestUtils.createTextField();
	}

	

	@After
	public void tearDown() throws Exception {
	}

	static TextField tf;

	@Test
	public void testShallowCopy() {
		TextField copy = tf.shallowCopy();
		assertEquals(DEFAULT, copy.getDefaultValue());
		assertEquals(RTFDATA, copy.getFieldData());

	}

	@Test
	public void testIsFieldForm() {
		assertTrue(tf.isTextField());
	}

}
