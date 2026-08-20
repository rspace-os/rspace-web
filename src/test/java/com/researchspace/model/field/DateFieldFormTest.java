package com.researchspace.model.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.model.record.TestFactory;

public class DateFieldFormTest {
  private static  DateFieldForm dft;
	@Before
	public void setUp() throws Exception {
		dft = TestFactory.createDateFieldForm();	
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@Test
	public void testCreateFieldSetsDEfaults (){
		DateField nf = (DateField)dft.createNewFieldFromForm();
		assertTrue(nf.getFieldData().contains("1970"));
		
		dft.setDefaultDate(0);
		DateField nf2 = (DateField)dft.createNewFieldFromForm();
		assertEquals("", nf2.getFieldData());
	}

	@Test
	public void testGetSummaryDoesNotTHrowNPEIfNoFieldsSet (){		
		System.err.println(dft.getSummary());
	}
	
	@Test
	public void testValidateData(){
		assertFalse(dft.validate("1970-01-23").hasErrorMessages()); //ok
		assertFalse(dft.validate("2100-01-23").hasErrorMessages()); //ok
		assertTrue(dft.validate("2100/01/23").hasErrorMessages()); //wrong format
		assertTrue(dft.validate("x").hasErrorMessages()); // wrong format
		assertFalse(dft.validate("").hasErrorMessages()); //may be blank if not set
		assertTrue(dft.validate("1970-01-01").hasErrorMessages()); //b4 min
		assertTrue(dft.validate("2287-01-01").hasErrorMessages()); // after max
	}
	
	@Test
	public void testShallowCopy() throws IllegalArgumentException, IllegalAccessException {		
		DateFieldForm copy = dft.shallowCopy();
		
		// use reflection help class to ensure fields are equals
		List<Class<? super DateFieldForm>> classesToConsider = new ArrayList<>();
		classesToConsider.add(DateFieldForm.class);
		classesToConsider.add(FieldForm.class);
		ModelTestUtils.assertCopiedFieldsAreEqual(copy, dft, Collections.EMPTY_SET	, classesToConsider);
		
	}
}
