package com.researchspace.model.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NumberFieldTest {

	NumberField nf;
	@Before
	public void setUp() throws Exception {
		NumberFieldForm nft = FieldTestUtils.createANumberFieldForm();
		nf = new NumberField(nft);
		
		
		nf.setId(5L);
		nf.setFieldData("122");
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testShallowCopy() {
		NumberField copy = nf.shallowCopy();
		assertNull(copy.getId());
		assertEquals(copy.getMinNumberValue(), nf.getMinNumberValue());
		assertEquals(copy.getMaxNumberValue(), nf.getMaxNumberValue());
		assertEquals(copy.getDefaultNumberValue(), nf.getDefaultNumberValue());
		assertEquals(copy.getDecimalPlaces(), nf.getDecimalPlaces());
	}

}
