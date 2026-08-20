package com.researchspace.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Utility class for manipulation / handling of URLS and links
 */
public class LinkUtils {
	public final static String HTTP_PREFIX = "http://";
	public final static String HTTPS_PREFIX = "https://";

	private final static Pattern HTTPPREFIX = Pattern.compile("http[/:]*");
	private final static Pattern HTTPSPREFIX = Pattern.compile("https[/:]*");

	/**
	 * Adds an http:// prefix to an external link if it doesn't have one, and
	 * attempts to replace a mistyped http or https prefix (e.g., http:/) with a
	 * correct one.
	 * 
	 * @param externalLinkURL
	 * @return a new string that may have been modified
	 */
	public static String modifyPrefix(String externalLinkURL) {
		if (!StringUtils.isBlank(externalLinkURL)) {
			if (externalLinkURL.trim().startsWith(HTTP_PREFIX) || externalLinkURL.trim().startsWith(HTTPS_PREFIX)) {
				return externalLinkURL;
			}
		}
		Matcher m = HTTPPREFIX.matcher(externalLinkURL);
		Matcher m2 = HTTPSPREFIX.matcher(externalLinkURL);
		if (m2.find()) {
			return m2.replaceFirst(HTTPS_PREFIX);
		} else if (m.find()) {
			return m.replaceFirst(HTTP_PREFIX);
		} else {
			return HTTP_PREFIX + externalLinkURL;
		}
	}

}
