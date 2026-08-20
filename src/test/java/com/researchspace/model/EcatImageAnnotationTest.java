package com.researchspace.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.researchspace.core.testutil.ModelTestUtils;


public class EcatImageAnnotationTest {

	private static final long FIELD_ID = 6L;
	EcatImageAnnotation ann;

	@Before
	public void setUp() throws Exception {
		ann = new EcatImageAnnotation();
	}

	@Test
	public void testShallowCopy() throws IllegalArgumentException, IllegalAccessException {
		ann.setAnnotations("annotations");
		ann.setTextAnnotations("text");
		ann.setData( new byte[10]);
		ann.setImageId(4L);
		ann.setId(1L);
		ann.setParentId(FIELD_ID);
		
		EcatImageAnnotation copy = ann.shallowCopy();
		Set<String> toExclude = ModelTestUtils.generateExclusionFieldsFrom("id", "fieldId");
		List<Class<? super EcatImageAnnotation>> classes = new ArrayList<>();
		classes.add(EcatImageAnnotation.class);
	    ModelTestUtils.assertCopiedFieldsAreEqual(copy, ann, toExclude, classes);
	}

}
