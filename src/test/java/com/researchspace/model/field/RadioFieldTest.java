package com.researchspace.model.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RadioFieldTest {

	RadioField nf;
	@Before
	public void setUp() throws Exception {
		RadioFieldForm rft = new RadioFieldForm();
		
		rft.setColumnIndex(0);
		rft.setName("Name");
		rft.setRadioOption("x=1&x=2&x=3");
		rft.setDefaultRadioOption("x=1");
		nf = new RadioField(rft);
		nf.setFieldData("1");
	
		nf.setId(5L);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testShallowCopy() {
		RadioField copy = nf.shallowCopy();
		assertNull(copy.getId());
		assertEquals(nf.getFieldData(),copy.getFieldData());
		assertEquals(nf.getDefaultRadioOption(),copy.getDefaultRadioOption());

		assertEquals(nf.getRadioOption(), copy.getRadioOption());	
	}
	
	@Test
	public void getRadioOptionAsListTest() {
		assertEquals(3,nf.getRadioOptionAsList().size());
	}

}
