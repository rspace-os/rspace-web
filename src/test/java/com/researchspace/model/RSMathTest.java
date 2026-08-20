package com.researchspace.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.model.record.TestFactory;



public class RSMathTest {

	private RSMath math;

	@Before
	public void setUp() throws Exception {
		math = TestFactory.createAMathElement();
		math.setId(2L);
		math.setField(TestFactory.createAnyField());
	}

	@Test
	public void testShallowCopy() throws IllegalArgumentException, IllegalAccessException {
		RSMath copy = math.shallowCopy();
		Set<String> toExclude = ModelTestUtils.generateExclusionFieldsFrom("id", "field");
		List<Class<? super RSMath>> classes = new ArrayList<>();
		classes.add(RSMath.class);
		ModelTestUtils.assertCopiedFieldsAreEqual(copy, math, toExclude, classes);
		math.toString(); // no NPE
	}

}
