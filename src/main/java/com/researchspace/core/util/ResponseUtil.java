package com.researchspace.core.util;

import java.util.Date;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Utility methods for manipulating the HTTP response directly
 */
public class ResponseUtil {
	private static final String LAST_MODIFIED_HDR = "Last-Modified";
	/**
	 * 
	 */
	public static final String CACHE_CONTROL_HDR = "Cache-Control";
	/**
	 * 
	 */
	public static final int HOUR = 60 * 60;
	/**
	 * 
	 */
	public static final int DAY = HOUR * 24;
	/**
	 * 
	 */
	public static final int YEAR = DAY * 365;

	/**
	 * Utility method to set caching headers
	 * 
	 * @param timeInSecondsToCache
	 * @param lastModified
	 * @param response
	 */
	public void setCacheTimeInBrowser(int timeInSecondsToCache, Date lastModified, HttpServletResponse response) {
		response.addHeader(CACHE_CONTROL_HDR, "max-age=" + timeInSecondsToCache);
		if (lastModified != null) {
			response.addDateHeader(LAST_MODIFIED_HDR, lastModified.getTime());
		}
	}

	/**
	 * Sets the filename of the downloaded file to the desired name, which can
	 * be different to the file's real name. E.g., it can be a display name.
	 * 
	 * @param response
	 * @param name
	 */
	public void setContentDisposition(HttpServletResponse response, String name) {
		String headerKey = "Content-Disposition";
		String headerValue = String.format("attachment; filename=\"%s\"", name);
		response.setHeader(headerKey, headerValue);
	}

	public void blockCache(HttpServletResponse response) {
		response.setHeader("Cache-Control", "max-age=0, no-cache, must-revalidate, no-store");
		response.setHeader("Pragma", "no-cache");
		response.setDateHeader("Expires", 0L);
	}

}
