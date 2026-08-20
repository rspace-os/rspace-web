package com.researchspace.model.utils;

public class Utils {
	public static void replaceTRailingSeparator(StringBuffer sb, String separator) {
		int indx = sb.lastIndexOf(separator);
		if (indx == sb.length() - 1) {
			sb.deleteCharAt(indx);
		}
	}
	
	public static void replaceTRailingSeparator(StringBuilder sb, String separator) {
		int indx = sb.lastIndexOf(separator);
		if (indx == sb.length() - 1) {
			sb.deleteCharAt(indx);
		}
	}

	public static Long convertToLongOrNull(String longNum) {
		try {
			return Long.parseLong(longNum);
		} catch (NumberFormatException nex){
			return null;
		}
	}
}
