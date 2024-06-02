package com.researchspace.webapp.controller;

import com.researchspace.core.util.RequestUtil;
import com.researchspace.core.util.TransformerUtils;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

/**
 * This is called around the lifecycle of a Controller and is used to log incoming requests. It is
 * configured in dispatcher-servlet.xml and will exclude login/signup information.
 *
 * <p>The aim is to produce a log file of high-level records that can be searched by a sysadmin or
 * auditor.
 */
public class LoggingInterceptor extends HandlerInterceptorAdapter {
  // default access for testing
  private static Logger log = LogManager.getLogger(LoggingInterceptor.class);

  public static Logger getLog() {
    return log;
  }

  // this pattern extracts the URL path up until an id term - this keeps the
  // number
  // of URLs that are tracked down and allows aggregation of timing results
  private Pattern urlName = Pattern.compile("(/[^0-9]+)");

  public Pattern getUrlNamePattern() {
    return urlName;
  }

  /**
   * This implementation always returns <code>true</code> and logs the incoming request (see
   * log4j2.xml to see how logging location is configured).
   */
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {

    IgnoreInLoggingInterceptor ignoreAnnotationPolicy = getIgnoreLoggingAnnotation(handler);
    // if we're a regular handler, see if logging is disabled or not.
    if (loggingShouldBeIgnored(ignoreAnnotationPolicy)) {
      return true;
    }

    if (loggingShouldOmitRequestParams(ignoreAnnotationPolicy)) {
      log.info(
          "["
              + request.getRequestURI()
              + "] from "
              + RequestUtil.remoteAddr(request)
              + " made by: ["
              + request.getRemoteUser()
              + "]");

    } else {
      Set<String> toIgnore = getParamsToIgnore(ignoreAnnotationPolicy);

      StringBuffer sb = new StringBuffer();
      Enumeration<?> reParamNames = request.getParameterNames();
      while (reParamNames.hasMoreElements()) {
        String paramName = (String) reParamNames.nextElement();
        if (toIgnore.contains(paramName)) {
          continue;
        }
        String[] paramValue = request.getParameterValues(paramName);
        // String[] truncated = truncateParamValueLengths(paramValue);
        String parmValuesAsStr = "";
        if (paramValue != null) {
          String[] truncated = truncateParamValueLengths(paramValue);
          parmValuesAsStr = StringUtils.join(truncated, ",");
        }
        sb.append(paramName + "=" + parmValuesAsStr + ",");
      }
      if (dataIsRawTemplate(sb, request)) {
        return false;
      }
      if (log.isInfoEnabled()) {
        log.info(
            "["
                + request.getRequestURI()
                + "] from "
                + RequestUtil.remoteAddr(request)
                + " with args: ["
                + sb.toString()
                + "]"
                + " made by: ["
                + request.getRemoteUser()
                + "]");
      }
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  private Set<String> getParamsToIgnore(IgnoreInLoggingInterceptor ignoreAnnotationPolicy) {
    if (ignoreAnnotationPolicy == null
        || ignoreAnnotationPolicy.ignoreRequestParams().length == 0) {
      return Collections.EMPTY_SET;
    } else {
      return TransformerUtils.toSet(ignoreAnnotationPolicy.ignoreRequestParams());
    }
  }

  private boolean dataIsRawTemplate(StringBuffer sb, HttpServletRequest request) {
    return request.getRequestURI().contains("thumbnail/data") && sb.toString().contains("{{");
  }

  protected boolean loggingShouldBeIgnored(IgnoreInLoggingInterceptor annot) {
    return annot != null && annot.ignoreAll();
  }

  protected IgnoreInLoggingInterceptor getIgnoreLoggingAnnotation(Object handler) {
    if (handler instanceof HandlerMethod) {
      HandlerMethod handlerNtv = (HandlerMethod) handler;
      IgnoreInLoggingInterceptor annot =
          handlerNtv.getMethodAnnotation(IgnoreInLoggingInterceptor.class);
      return annot;
    }
    return null;
  }

  protected boolean loggingShouldOmitRequestParams(IgnoreInLoggingInterceptor annot) {
    return annot != null && annot.ignoreAllRequestParams();
  }

  String extractTagFromURL(String url) {
    String tag = url;
    Matcher m = urlName.matcher(url);
    if (m.find()) {
      tag = m.group();
      // shorten URLs to appear as tags; these will be chart labels.
      tag = tag.replace("/ajax", "");
      tag = tag.replace("/app", "");
      tag = tag.replace("notebook", "nbk");
      tag = tag.replace("editor", "ed");
      tag = tag.replace("structuredDocument", "sdc");
    }
    return tag;
  }

  private String[] truncateParamValueLengths(String[] paramValue) {
    final int maxWidth = 100;

    String[] copies = new String[paramValue.length];
    System.arraycopy(paramValue, 0, copies, 0, paramValue.length);
    for (int i = 0; i < copies.length; i++) {
      if (!StringUtils.isBlank(copies[i])) {
        copies[i] = StringUtils.abbreviate(copies[i], maxWidth);
      }
    }
    return copies;
  }
}
