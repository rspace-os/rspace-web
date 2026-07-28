package com.researchspace.model.inventory;

import org.apache.commons.lang3.StringUtils;

/**
 * Utility class for naming series of samples and subsamples.
 */
public class InventorySeriesNamingHelper {
	
	/**  
	 * Takes baseName and adds counter as a suffix.
	 * Suffix will be minimum two-digits, zero-prefixed if necessary.
	 */
	public static String getSerialNameForSubSample(String baseName, int currentCount, Integer totalCount) {
		int suffixLength = Math.max(2, String.valueOf(totalCount).length());
		return String.format("%s.%0" +  suffixLength + "d", baseName, currentCount);
	}

	/**
	 * Takes baseName and adds counter as a suffix.
	 * Suffix will not be zero-prefixed.
	 */
	public static String getSerialNameForSubSampleNoZeroPrefix(String baseName, int currentCount) {
		return String.format("%s.%d", baseName, currentCount);
	}


	/**
	 * Returns baseName with counter added as a suffix.
	 * Suffix will be minimum two-digits, zero-prefixed if necessary.
	 */
	public static String getSerialNameForSample(String baseName, int currentCount, Integer totalCount) {
		int suffixLength = Math.max(2, String.valueOf(totalCount).length()); 
		return String.format("%s-%0" +  suffixLength + "d", baseName, currentCount);
	}
}