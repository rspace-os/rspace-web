package com.researchspace.model.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StringFieldTest {

	StringField nf;
	@Before
	public void setUp() throws Exception {
		nf = new StringField(new StringFieldForm());
		nf.setColumnIndex(0);
		nf.setName("Name");
		nf.setFieldData("data");
		nf.setId(5L);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testShallowCopy() {
		StringField copy = nf.shallowCopy();
		assertNull(copy.getId());
		assertEquals(nf.getFieldData(),copy.getFieldData());
		
	}

}
