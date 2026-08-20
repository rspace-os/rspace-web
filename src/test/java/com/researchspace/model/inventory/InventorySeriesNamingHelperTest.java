package com.researchspace.model.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.Test;

public class InventorySeriesNamingHelperTest {

	@Test
	public void testSampleSubSampleNaming() {

		assertEquals("sampleName-05", InventorySeriesNamingHelper.getSerialNameForSample("sampleName", 5, 5));
		assertEquals("sampleName-05", InventorySeriesNamingHelper.getSerialNameForSample("sampleName", 5, 25));
		assertEquals("sampleName-15", InventorySeriesNamingHelper.getSerialNameForSample("sampleName", 15, 99));
		assertEquals("sampleName-015", InventorySeriesNamingHelper.getSerialNameForSample("sampleName", 15, 100));
		assertEquals("sampleName-0015", InventorySeriesNamingHelper.getSerialNameForSample("sampleName", 15, 1500));
		
		assertEquals("sampleName.05", InventorySeriesNamingHelper.getSerialNameForSubSample("sampleName", 5, 5));
		assertEquals("sampleName-05.05", InventorySeriesNamingHelper.getSerialNameForSubSample("sampleName-05", 5, 5));
		assertEquals("sampleName-05.15", InventorySeriesNamingHelper.getSerialNameForSubSample("sampleName-05", 15, 15));

		assertEquals("sampleName-05.5", InventorySeriesNamingHelper.getSerialNameForSubSampleNoZeroPrefix("sampleName-05", 5));
		assertEquals("sampleName-05.15", InventorySeriesNamingHelper.getSerialNameForSubSampleNoZeroPrefix("sampleName-05", 15));
	}

}
