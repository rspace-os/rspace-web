package com.researchspace.webapp.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import org.sitemesh.config.ConfigurableSiteMeshFilter;

/**
 * Makes SiteMesh select decorators (and apply excludes) from the full incoming request path.
 *
 * <p>On a dispatch that carries forward attributes, SiteMesh resolves the request path from {@code
 * jakarta.servlet.forward.servlet_path}. Every RSpace request is forwarded to {@code /app/*} by the
 * UrlRewriteFilter, so that attribute is the original request's servlet path. The DispatcherServlet
 * is additionally mapped to module-root prefixes ({@code /workspace/*}, {@code /system/*}, ...) so
 * Tomcat can resolve multipart upload limits from the originally-matched servlet (see {@code
 * DispatcherServletInitializer}). For a request like {@code /workspace/ajax/search} that attribute
 * is then only {@code /workspace}, so path-based mappings such as the {@code &#42;/ajax/&#42;}
 * exclude in {@code sitemesh3.xml} would not see the full path.
 *
 * <p>This filter presents {@code forward.servlet_path} as the full forwarded request URI so the
 * mappings see the complete path, as they are written to expect. It changes nothing about routing
 * or multipart handling. SiteMesh is not mapped to FORWARD dispatches (see {@code web.xml}), so
 * this only applies to ERROR dispatches that carry forward attributes.
 */
public class FullPathSiteMeshFilter extends ConfigurableSiteMeshFilter {

  static final String FORWARD_SERVLET_PATH = "jakarta.servlet.forward.servlet_path";
  static final String FORWARD_REQUEST_URI = "jakarta.servlet.forward.request_uri";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (request instanceof HttpServletRequest http) {
      request = withFullForwardPath(http);
    }
    super.doFilter(request, response, chain);
  }

  /**
   * Returns a request whose {@code forward.servlet_path} attribute reports the full forwarded URI
   * (context path stripped). If the request was not forwarded (no {@code forward.request_uri}), the
   * original request is returned unchanged.
   */
  static HttpServletRequest withFullForwardPath(HttpServletRequest http) {
    if (!(http.getAttribute(FORWARD_REQUEST_URI) instanceof String forwardUri)) {
      return http;
    }
    String contextPath = http.getContextPath();
    String fullPath =
        contextPath != null && !contextPath.isEmpty() && forwardUri.startsWith(contextPath)
            ? forwardUri.substring(contextPath.length())
            : forwardUri;
    return new HttpServletRequestWrapper(http) {
      @Override
      public Object getAttribute(String name) {
        return FORWARD_SERVLET_PATH.equals(name) ? fullPath : super.getAttribute(name);
      }
    };
  }
}
