package com.researchspace.model.field;

import com.researchspace.core.testutil.ModelTestUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class TimeFieldFormTest {
	TimeFieldForm tft;
	@Before
	public void setUp() throws Exception {
		tft= new TimeFieldForm();
		tft.setColumnIndex(3);
		tft.setDefaultTime(12345L);
		tft.setMaxTime(123456L);
		tft.setMinTime(1234L);
		tft.setTimeFormat("hh-mm-ss");
	}

	@Test
	public void testShallowCopy() throws IllegalArgumentException, IllegalAccessException {
		TimeFieldForm copy = tft.shallowCopy();
		
		// use reflection help class to ensure fields are equals
		List<Class<? super TimeFieldForm>> classesToConsider = new ArrayList<>();
		classesToConsider.add(TimeFieldForm.class);
		classesToConsider.add(FieldForm.class);
		ModelTestUtils.assertCopiedFieldsAreEqual(copy, tft, Collections.EMPTY_SET	, classesToConsider);
	}

}
