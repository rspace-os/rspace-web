package com.researchspace.model.field;

import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.model.record.TestFactory;

public class TextFieldFormTest {

	TextFieldForm sft;

	@Before
	public void setUp() throws Exception {
		sft = TestFactory.createTextFieldForm();
		sft.setDeleted(true);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testValidate() {
		// no validatio non text fields yet!
		assertFalse(sft.validate(null).hasErrorMessages());
		assertFalse(sft.validate("").hasErrorMessages());
		assertFalse(sft.validate("x").hasErrorMessages());
	}

	@Test
	public void testShallowCopy() throws IllegalArgumentException, IllegalAccessException {

		TextFieldForm copy = sft.shallowCopy();

		// use reflection help class to ensure fields are equals
		List<Class<? super TextFieldForm>> classesToConsider = new ArrayList<>();
		classesToConsider.add(TextFieldForm.class);
		classesToConsider.add(FieldForm.class);
		ModelTestUtils.assertCopiedFieldsAreEqual(copy, sft, Collections.EMPTY_SET, classesToConsider);

	}

}
