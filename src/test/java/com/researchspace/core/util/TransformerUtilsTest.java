package com.researchspace.core.util;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static com.researchspace.core.util.TransformerUtils.toEnums;
import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.core.util.TransformerUtils.toSet;
import static com.researchspace.core.util.TransformerUtils.transform;
import static com.researchspace.core.util.TransformerUtils.transformToString;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import lombok.Data;

public class TransformerUtilsTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testTransform() {
		Set<Object> set = TransformerUtils.toSet(new Object());
		List<Object> transformed = transform(set, new Transformer<>() {
			// null transformer, just for testing
			@Override
			public Object transform(Object toTransform) {
				// TODO Auto-generated method stub
				return toTransform;
			}
		});
		assertEquals(1, transformed.size());
	}

	@Test
	public void testToSet() {
		assertEquals(1, toSet(new Object()).size());
		assertEquals(0, toSet(null).size());
		assertEquals(0, toSet(new Object[]{}).size());
	}
	
	@Test
	public void testToList() {
		assertEquals(1, toList(new Object()).size());
		assertEquals(0, toList(null).size());
		assertEquals(0, toList(new Object[]{}).size());
	}

	@Test
	public void testStringArrayToEnum() {
		assertEquals(EnumSet.of(SortOrder.ASC), toEnums(new String[] { "ASC" }, SortOrder.class));
	}
	@Data
	public static class Any {
		private String x = "abcde";
	}
	
	@Test
	public void testTransformToString() throws Exception {
		final Any any = new Any();
		final List<Any> anys = toList(any);
		assertEquals("abcde", transformToString(anys, "x").get(0));
		assertEquals(0, transformToString(new ArrayList<>(), "x").size());
		assertExceptionThrown(()->transformToString(anys, "y"), IllegalArgumentException.class);
	}
}
