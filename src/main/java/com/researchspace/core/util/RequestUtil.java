package com.researchspace.core.util;

import javax.servlet.ServletRequest;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience class for setting and retrieving cookies, and examining the
 * response.
 */
public final class RequestUtil {
	private static final Logger LOG = LoggerFactory.getLogger(RequestUtil.class);

	/**
	 * Checkstyle rule: utility classes should not have public constructor
	 */
	private RequestUtil() {
	}

	/**
	 * 
	 */
	public static final String AJAX_REQUEST_HEADER_NAME = "X-Requested-With";
	/**
	 * 
	 */
	public static final String AJAX_REQUEST_TYPE = "XMLHttpRequest";

	/**
	 * Optional request header that keeps original request IP address
	 */
	public static final String HEADER_X_FORWARDED_FOR = "X-FORWARDED-FOR";

	/**
	 * Convenience method to set a cookie
	 * 
	 * @param response
	 *            the current response
	 * @param name
	 *            the name of the cookie
	 * @param value
	 *            the value of the cookie
	 * @param path
	 *            the path to set it on
	 */
	public static void setCookie(HttpServletResponse response, String name, String value, String path) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Setting cookie '" + name + "' on path '" + path + "'");
		}

		final int maxAge = 3600 * 24 * 30; // 30 days
		Cookie cookie = new Cookie(name, value);
		cookie.setSecure(false);
		cookie.setPath(path);
		cookie.setMaxAge(maxAge);
		response.addCookie(cookie);
	}

	/**
	 * Convenience method to get a cookie by name
	 * 
	 * @param request
	 *            the current request
	 * @param name
	 *            the name of the cookie to find
	 * 
	 * @return the cookie (if found), null if not found
	 */
	public static Cookie getCookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		Cookie returnCookie = null;

		if (cookies == null) {
			return returnCookie;
		}

		for (final Cookie thisCookie : cookies) {
			if (thisCookie.getName().equals(name) && !"".equals(thisCookie.getValue())) {
				returnCookie = thisCookie;
				break;
			}
		}

		return returnCookie;
	}

	/**
	 * Convenience method for deleting a cookie by name
	 * 
	 * @param response
	 *            the current web response
	 * @param cookie
	 *            the cookie to delete
	 * @param path
	 *            the path on which the cookie was set (i.e. /appfuse)
	 */
	public static void deleteCookie(HttpServletResponse response, Cookie cookie, String path) {
		if (cookie != null) {
			// Delete the cookie by setting its maximum age to zero
			cookie.setMaxAge(0);
			cookie.setPath(path);
			response.addCookie(cookie);
		}
	}

	/**
	 * Convenience method to get the application's URL based on request
	 * variables.
	 * 
	 * @param request
	 *            the current request
	 * @return URL to application
	 */
	public static String getAppURL(HttpServletRequest request) {
		final int port80 = 80;
		final int port443 = 443;

		if (request == null) {
			return "";
		}

		StringBuffer url = new StringBuffer();
		int port = request.getServerPort();
		if (port < 0) {
			port = port80; // Work around java.net.URL bug
		}
		String scheme = request.getScheme();
		url.append(scheme);
		url.append("://");
		url.append(request.getServerName());
		if ((scheme.equals("http") && (port != port80)) || (scheme.equals("https") && (port != port443))) {
			url.append(':');
			url.append(port);
		}
		url.append(request.getContextPath());
		return url.toString();
	}

	/**
	 * Boolean test for whether request is an Ajax REquest or not.
	 * 
	 * @param request
	 * @return
	 */
	public static boolean isAjaxRequest(ServletRequest request) {
		if (request instanceof HttpServletRequest) {
			return AJAX_REQUEST_TYPE.equals(((HttpServletRequest) request).getHeader(AJAX_REQUEST_HEADER_NAME));
		} else {
			return false;
		}
	}

	/**
	 * Gets the ip address of the caller.
	 * <ul>
	 * <li>If header 'HEADER_X_FORWARDED_FOR' is set, returns this.
	 * <li>Otherwise, returns request.getRemoteAddr()
	 * <li>If remoteAddress is null or empty, returns "unknown";
	 * <ul/>
	 * 
	 * @param request
	 * @return A non-empty string
	 */
	public static String remoteAddr(HttpServletRequest request) {
		if (request == null || StringUtils.isEmpty(request.getRemoteAddr())) {
			return "unknown";
		}
		String remoteAddr = request.getRemoteAddr();

		String value;
		if ((value = request.getHeader(HEADER_X_FORWARDED_FOR)) != null) {
			remoteAddr = value;
			int idx = remoteAddr.indexOf(',');
			if (idx > -1) {
				remoteAddr = remoteAddr.substring(0, idx);
			}
		}
		return remoteAddr;
	}
}
