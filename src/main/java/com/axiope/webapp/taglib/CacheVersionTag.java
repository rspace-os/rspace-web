package com.axiope.webapp.taglib;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

/**
 * Emits just the cache-busting version token resolved by {@link FrontendCacheVersion#resolve} —
 * intended for places that need the raw token (e.g. exposing it to client-side JS via {@code
 * window.RS.cacheVersion}) rather than appending {@code ?v=<token>} to a URL.
 *
 * <p>Output is HTML-escaped; nothing is written when no token is available.
 */
public class CacheVersionTag extends TagSupport {

  private static final long serialVersionUID = 1L;

  @Override
  public int doStartTag() throws JspException {
    HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
    String version = FrontendCacheVersion.resolve(pageContext.getServletContext(), request);
    if (StringUtils.isBlank(version)) {
      return SKIP_BODY;
    }
    try {
      pageContext.getOut().write(StringEscapeUtils.escapeHtml4(version));
    } catch (IOException e) {
      throw new JspException("Failed to write cache version token", e);
    }
    return SKIP_BODY;
  }
}
