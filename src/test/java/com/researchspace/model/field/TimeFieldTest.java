package com.researchspace.model.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TimeFieldTest {

	TimeField nf;
	@Before
	public void setUp() throws Exception {
		TimeFieldForm tft = new TimeFieldForm();
		tft.setDefaultTime(123L);
		tft.setMaxTime(345);
		tft.setMinTime(123);
		tft.setColumnIndex(0);
	
		nf = new TimeField(tft);
		
		nf.setName("Name");
		nf.setFieldData("data");
		
	
		nf.setId(5L);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testShallowCopy() {
		TimeField copy = nf.shallowCopy();
		assertNull(copy.getId());
		assertEquals(nf.getFieldData(),copy.getFieldData());
		assertEquals(nf.getDefaultTime(),copy.getDefaultTime());
		assertEquals(nf.getMaxTime(),copy.getMaxTime());
		assertEquals(nf.getMinTime(),copy.getMinTime());
		
	}

}
