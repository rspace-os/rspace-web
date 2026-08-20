package com.researchspace.core.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CollectionFilterTest {

	@Test
	public void testFilterOfNoopsImplmentations() {
		assertTrue(CollectionFilter.NO_FILTER.filter(new Object()));
		assertFalse(CollectionFilter.NULLFILTER.filter(new Object()));
	}

}
