package com.researchspace.core.util;

import static com.researchspace.core.util.RSCollectionUtils.mergeLists;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.Test;

public class RSCollectionUtilsTest {

	@Test
	public void testMergeLists() {

		List<Integer> l1 = Arrays.asList(new Integer[] { 1, 3, 5, 7, 9 });
		List<Integer> l2 = Arrays.asList(new Integer[] { 7, 9, 11, 13 });
		List<Integer> EXPECTED = Arrays.asList(new Integer[] { 1, 3, 5, 7, 9, 11, 13 });

		List<Integer> result = mergeLists(l1, l2);
		assertTrue(CollectionUtils.isEqualCollection(EXPECTED, result));

		List<Integer> result2 = mergeLists(l2, l1);
		assertTrue(CollectionUtils.isEqualCollection(EXPECTED, result2));

		l1 = Arrays.asList(new Integer[] { 1, 3 });
		l2 = Arrays.asList(new Integer[] { 7, 9, 11, 13 });
		assertNull(mergeLists(l1, l2));

		l1 = Arrays.asList(new Integer[] { 1, 3, 7 });
		l2 = Arrays.asList(new Integer[] { 1, 3, 7 });
		assertTrue(CollectionUtils.isEqualCollection(l1, mergeLists(l1, l2)));
		assertTrue(CollectionUtils.isEqualCollection(l2, mergeLists(l1, l2)));

		l1 = Arrays.asList(new Integer[] {});
		l2 = Arrays.asList(new Integer[] {});
		// 2 empty collections have no common element
		assertNull(mergeLists(l1, l2));

		// noncontiguous:
		l1 = Arrays.asList(new Integer[] { 1, 3, 5, 7, 9 });
		l2 = Arrays.asList(new Integer[] { 5, 6, 7, 8 });
		System.err.println(mergeLists(l1, l2));
	}

}
