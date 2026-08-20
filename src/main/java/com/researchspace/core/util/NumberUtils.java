package com.researchspace.core.util;

public class NumberUtils {

	/**
	 * <p>
	 * Convert a <code>String</code> to an <code>int</code>, returning a default
	 * value if the conversion fails.
	 * </p>
	 * (from deprecated method in apache common.lang)
	 * 
	 * @param str
	 *            the string to convert
	 * @param defaultValue
	 *            the default value
	 * @return the int represented by the string, or the default if conversion
	 *         fails
	 */
	public static int stringToInt(String str, int defaultValue) {
		try {
			return Integer.parseInt(str);
		} catch (NumberFormatException nfe) {
			return defaultValue;
		}
	}

}
