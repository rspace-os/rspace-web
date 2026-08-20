package com.researchspace.model.field;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for String matching.
 */
public class FieldUtils {
	private static final Pattern PATTERN = Pattern.compile("(\\S+=[^&=]+&?)+(\\S+=[^&=])*");
	private static final Pattern SUBPATTERN = Pattern.compile("\\S+=[^&=]+");

	public static boolean isValidRadioOrChoiceString(String toTest) {
		Matcher matcher = PATTERN.matcher(toTest);
		if (!matcher.matches()) {
			return false;
		}
		String[] values = toTest.split("&");
		for (String val : values) {
			Matcher matcher2 = SUBPATTERN.matcher(val);
			if (!matcher2.matches()) {
				return false;
			}
		}
		return true;
	}

}
