package com.researchspace.model.record;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class FilterDeletedRecordsTest {
	
	RecordFilter filter;
	
	@Before
	public void setUp() throws Exception {
		filter = new FilterDeletedRecords();
	}

	@Test
	public void testFilter() {
		StructuredDocument sd = TestFactory.createAnySD(TestFactory.createAnyForm());
		assertTrue(filter.filter(sd));
		sd.setDeleted(true);
		assertFalse(filter.filter(sd));
	}

}
