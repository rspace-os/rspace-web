package com.researchspace.core.util;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

/** Convenience methods for examining the request. */
public final class RequestUtil {

  /** Checkstyle rule: utility classes should not have public constructor */
  private RequestUtil() {}

  /** */
  public static final String AJAX_REQUEST_HEADER_NAME = "X-Requested-With";

  /** */
  public static final String AJAX_REQUEST_TYPE = "XMLHttpRequest";

  /** Optional request header that keeps original request IP address */
  public static final String HEADER_X_FORWARDED_FOR = "X-FORWARDED-FOR";

  /**
   * Convenience method to get the application's URL based on request variables.
   *
   * @param request the current request
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
    if ((scheme.equals("http") && (port != port80))
        || (scheme.equals("https") && (port != port443))) {
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
      return AJAX_REQUEST_TYPE.equals(
          ((HttpServletRequest) request).getHeader(AJAX_REQUEST_HEADER_NAME));
    } else {
      return false;
    }
  }

  /**
   * Gets the ip address of the caller.
   *
   * <ul>
   *   <li>If header 'HEADER_X_FORWARDED_FOR' is set, returns this.
   *   <li>Otherwise, returns request.getRemoteAddr()
   *   <li>If remoteAddress is null or empty, returns "unknown";
   *       <ul/>
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
