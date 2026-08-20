package com.researchspace.model.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.core.testutil.ModelTestUtils;

public class NumberFieldFormTest {

	NumberFieldForm nft;

	@Before
	public void setUp() throws Exception {
		nft = FieldTestUtils.createANumberFieldForm();
	}

	

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetSummary() {
		assertNotNull(nft.getSummary());
	}

	@Test
	public void testCreateFieldSetsDEfaults() {
		NumberField nf = (NumberField) nft.createNewFieldFromForm();
		assertEquals("23.0", nf.getFieldData());

		// if set to be integer, should return integerrepresentation in string
		nft.setDecimalPlaces((byte) 0);
		NumberField nf3 = (NumberField) nft.createNewFieldFromForm();
		assertEquals("23", nf3.getFieldData());

		nft.setDefaultNumberValue(null);
		NumberField nf2 = (NumberField) nft.createNewFieldFromForm();
		assertEquals("", nf2.getFieldData());

	}

	@Test
	public void testValidate() {
		assertFalse(nft.validate("500").hasErrorMessages());
		assertTrue(nft.validate("5").hasErrorMessages());
		assertTrue(nft.validate("5000").hasErrorMessages());
		assertTrue(nft.validate("500.987").hasErrorMessages()); // too many dp
		assertFalse(nft.validate("500.98").hasErrorMessages()); // exact
		assertFalse(nft.validate("500.9").hasErrorMessages());// less
		assertFalse(nft.validate("").hasErrorMessages());// empyt is OK to store
	}

	
	@Test
	public void testValidateWhenNoDefaultSet() {
		nft = new NumberFieldForm("any");
		nft.setDecimalPlaces((byte)2);
		nft.setMaxNumberValue(5d);
		nft.setMinNumberValue(1d);
		// no default ( so default - value is set to 0, which means the value
		// will be set to 0
		assertFalse(nft.validate(null).hasErrorMessages());
		assertFalse(nft.validate("").hasErrorMessages());
		
		// the value is set to a non-default value,, so should be validated.
		assertTrue(nft.validate("0.1").hasErrorMessages());
		assertTrue(nft.validate("0.0").hasErrorMessages());
		
	}
	@Test
	public void testShallowCopy() throws IllegalArgumentException, IllegalAccessException {

		NumberFieldForm copy = nft.shallowCopy();

		// use reflection help class to ensure fields are equals
		List<Class<? super NumberFieldForm>> classesToConsider = new ArrayList<>();
		classesToConsider.add(NumberFieldForm.class);
		classesToConsider.add(FieldForm.class);
		ModelTestUtils.assertCopiedFieldsAreEqual(copy, nft, Collections.EMPTY_SET,
				classesToConsider);

	}

}
