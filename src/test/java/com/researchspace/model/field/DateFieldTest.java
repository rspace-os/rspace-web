package com.researchspace.model.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.record.TestFactory;

public class DateFieldTest {

	DateField nf;
	@Before
	public void setUp() throws Exception {
		nf = new DateField(TestFactory.createDateFieldForm());
		
		nf.setFieldData("1976-05-05");
	
	
		nf.setId(5L);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testShallowCopy() {
		DateField copy = nf.shallowCopy();
		assertNull(copy.getId());
		assertEquals(nf.getFieldData(),copy.getFieldData());
		assertEquals(nf.getDefaultDate(),copy.getDefaultDate());
		assertEquals(nf.getMaxValue(),copy.getMaxValue());
		assertEquals(nf.getMinValue(),copy.getMinValue());
		
	}

}
