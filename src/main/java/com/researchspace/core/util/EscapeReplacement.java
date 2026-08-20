package com.researchspace.core.util;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

/**
 * Special characters that are not acceptable to UNIX/HTML should be replace by
 * under score Feel free to add new escape character in the escape string
 * 
 * @Sunny
 */
public class EscapeReplacement {

	protected static final String ESCAPE = "<>&\"!#$%\' ()*+,/=?@[]^|{}~\u00A0\u2007\u202F";
	protected static final char REPLACEMENT = '_';

	public static String replaceChars(String str) {
		String sx = escapeXMLChar(str);
		StringCharacterIterator iterator = new StringCharacterIterator(str);
		char character = iterator.current();
		while (character != CharacterIterator.DONE) {
			int idx = ESCAPE.indexOf(character);
			if (idx >= 0) {
				char chx = ESCAPE.charAt(idx);
				sx = sx.replace(chx, REPLACEMENT);
			} else if (Character.isWhitespace(character)){
				sx = sx.replace(character, REPLACEMENT);
			}
			character = iterator.next();
		}
		return sx;
	}

	/**
	 * Escape characters for text appearing as XML data, between tags. The
	 * following characters are replaced with corresponding character entities :
	 * < -->&lt; >-->&gt; &-->&amp; " -> &quot; '--> &#039;
	 */
	static String escapeXMLChar(String str) {
		String str1 = str.replaceAll("&#[0-9][0-9];", "_");
		str1 = str1.replaceAll(" {2,}", " "); // replace double space with
												// single space
		return str1;
	}

	public static String convertTagSign(String str) {
		String str1 = str.replaceAll("&lt;", "<");
		str1 = str1.replaceAll("&gt;", ">");
		str1 = str1.replaceAll("&amp;", "&");
		str1 = str1.replaceAll("&quot;", "\"");
		str1 = str1.replaceAll("&#039;", "\'");
		str1 = str1.replaceAll("%3A", ":");
		str1 = str1.replace("%2F", "/");
		str1 = str1.replace("\n", " ");
		return str1;
	}

	public static String convertURLEncode(String str) {
		String str1 = str.replaceAll("%20", " ");
		return str1;
	}
}
