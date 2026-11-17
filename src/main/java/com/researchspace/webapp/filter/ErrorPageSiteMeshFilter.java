package com.researchspace.webapp.filter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import org.sitemesh.config.ConfigurableSiteMeshFilter;

/**
 * Makes SiteMesh match container error pages by the error page's own path ({@code /404.jsp}, {@code
 * /error.jsp}), not by the URL that failed.
 *
 * <p>SiteMesh takes the request path from {@code jakarta.servlet.forward.servlet_path} when it is
 * set. Tomcat sets it on the error-page dispatch, from the failed request; Jetty does not. Without
 * this filter the {@code sitemesh3.xml} rules for error pages would only work on Jetty, and on
 * Tomcat the {@code /error.jsp} exclude would never match.
 */
public class ErrorPageSiteMeshFilter extends ConfigurableSiteMeshFilter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (request instanceof HttpServletRequest http
        && http.getDispatcherType() == DispatcherType.ERROR) {
      request = withoutForwardServletPath(http);
    }
    super.doFilter(request, response, chain);
  }

  private static HttpServletRequest withoutForwardServletPath(HttpServletRequest http) {
    return new HttpServletRequestWrapper(http) {
      @Override
      public Object getAttribute(String name) {
        return RequestDispatcher.FORWARD_SERVLET_PATH.equals(name)
            ? null
            : super.getAttribute(name);
      }
    };
  }
}
