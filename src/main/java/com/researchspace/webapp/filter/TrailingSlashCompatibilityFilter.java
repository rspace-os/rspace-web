package com.researchspace.webapp.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.springframework.web.filter.UrlHandlerFilter;

/**
 * Transparently handles request paths with a trailing slash as if the slash were absent. Spring 5
 * matched "/foo/" to a handler mapped at "/foo" by default; Spring 6 turned that off and deprecated
 * the compatibility flag, but external API clients and old bookmarks may still use trailing-slash
 * URLs. This filter keeps parity via {@link UrlHandlerFilter}, Spring's replacement for the
 * deprecated flag. It must run before the UrlRewriteFilter forward so the dispatcher only ever sees
 * canonical (slash-less) paths; handler mappings must therefore not end in "/".
 *
 * <p>{@link UrlHandlerFilter} is builder-only and web.xml needs a filter with a no-arg constructor,
 * hence this thin wrapper.
 */
public class TrailingSlashCompatibilityFilter implements Filter {

  private final UrlHandlerFilter delegate =
      UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build();

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    delegate.init(filterConfig);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    delegate.doFilter(request, response, chain);
  }

  @Override
  public void destroy() {
    delegate.destroy();
  }
}
