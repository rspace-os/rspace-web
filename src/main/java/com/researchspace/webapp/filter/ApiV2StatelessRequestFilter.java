package com.researchspace.webapp.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/** Removes ambient browser cookies and servlet-session access from REST API v2 requests. */
public final class ApiV2StatelessRequestFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!(request instanceof HttpServletRequest httpRequest)) {
      filterChain.doFilter(request, response);
      return;
    }
    filterChain.doFilter(new StatelessRequest(httpRequest), response);
  }

  private static final class StatelessRequest extends HttpServletRequestWrapper {

    private StatelessRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public Cookie[] getCookies() {
      return null;
    }

    @Override
    public String getHeader(String name) {
      return "Cookie".equalsIgnoreCase(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return "Cookie".equalsIgnoreCase(name)
          ? Collections.emptyEnumeration()
          : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Enumeration<String> headerNames = super.getHeaderNames();
      if (headerNames == null) {
        return Collections.emptyEnumeration();
      }
      List<String> names = Collections.list(headerNames);
      names.removeIf("Cookie"::equalsIgnoreCase);
      return Collections.enumeration(names);
    }

    @Override
    public HttpSession getSession(boolean create) {
      if (create) {
        throw new IllegalStateException("REST API v2 requests cannot create browser sessions");
      }
      return null;
    }

    @Override
    public HttpSession getSession() {
      return getSession(true);
    }

    @Override
    public String changeSessionId() {
      throw new IllegalStateException("REST API v2 requests cannot change browser sessions");
    }

    @Override
    public String getRequestedSessionId() {
      return null;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
      return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
      return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
      return false;
    }
  }
}
