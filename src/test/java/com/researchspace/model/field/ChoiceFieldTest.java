package com.researchspace.model.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ChoiceFieldTest {

	ChoiceField nf;
	@Before
	public void setUp() throws Exception {
		ChoiceFieldForm cft = new ChoiceFieldForm();
	
		cft.setMultipleChoice(true);
		cft.setDefaultChoiceOption("x=2");
		cft.setChoiceOptions("x=2&x=3");
		nf = new ChoiceField(cft);
		nf.setColumnIndex(0);
		nf.setName("Name");
		nf.setFieldData("x=2");
		nf.setId(5L);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testShallowCopy() {
		ChoiceField copy = nf.shallowCopy();
		assertNull(copy.getId());
		assertEquals(nf.getFieldData(),copy.getFieldData());	
	}
	
	@Test
	public void getRadioOptionAsListTest() {
		assertEquals("2", nf.getChoiceOptionSelectedAsString());
		assertEquals(2,nf.getChoiceOptionAsList().size());
	}

}
