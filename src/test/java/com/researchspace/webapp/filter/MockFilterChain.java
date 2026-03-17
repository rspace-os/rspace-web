package com.researchspace.webapp.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Borrowed from the Display Tag project:
 * http://displaytag.sourceforge.net/xref-test/org/displaytag/filter/MockFilterSupport.html
 *
 * <p>Todo: look into using Spring's MockFilterChain:
 * http://www.springframework.org/docs/api/org/springframework/mock/web/MockFilterChain.html
 */
public class MockFilterChain implements FilterChain {
  private final Logger log = LoggerFactory.getLogger(MockFilterChain.class);
  private String forwardURL;

  public void doFilter(ServletRequest request, ServletResponse response)
      throws IOException, ServletException {
    String uri = ((HttpServletRequest) request).getRequestURI();
    String requestContext = ((HttpServletRequest) request).getContextPath();

    if (StringUtils.isNotEmpty(requestContext) && uri.startsWith(requestContext)) {
      uri = uri.substring(requestContext.length());
    }

    this.forwardURL = uri;
    log.debug("Forwarding to: " + uri);

    RequestDispatcher dispatcher = request.getRequestDispatcher(uri);
    dispatcher.forward(request, response);
  }

  public String getForwardURL() {
    return this.forwardURL;
  }
}
