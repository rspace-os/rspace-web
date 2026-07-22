package com.researchspace.webapp.filter;

import com.researchspace.Constants;
import com.researchspace.service.UserLocaleService;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.jstl.core.Config;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies {@link UserLocaleService}'s locale consistently throughout each request. */
public class LocaleFilter extends OncePerRequestFilter {

  public static final String RESOLVED_LOCALE_TAG_REQUEST_ATTRIBUTE = "rsResolvedLocaleTag";

  private UserLocaleService userLocaleService;

  @Override
  protected void initFilterBean() throws ServletException {
    // This filter is created by the servlet container, not Spring.
    userLocaleService =
        WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext())
            .getBean(UserLocaleService.class);
  }

  /**
   * @param request the current request
   * @param response the current response
   * @param chain the chain
   * @throws IOException when something goes wrong
   * @throws ServletException when a communication failure happens
   */
  @SuppressWarnings("unchecked")
  public void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    Locale locale = resolveLocale(request);
    LocaleContext previousLocaleContext = LocaleContextHolder.getLocaleContext();
    LocaleContextHolder.setLocale(locale);
    request.setAttribute(RESOLVED_LOCALE_TAG_REQUEST_ATTRIBUTE, locale.toLanguageTag());

    HttpSession session = request.getSession(false);
    if (session != null) {
      session.setAttribute(Constants.PREFERRED_LOCALE_KEY, locale);
      Config.set(session, Config.FMT_LOCALE, locale);
    }
    if (!(request instanceof LocaleRequestWrapper)) {
      request = new LocaleRequestWrapper(request, locale);
    }

    String theme = request.getParameter("theme");
    if (theme != null && request.isUserInRole(Constants.ADMIN_ROLE)) {
      Map<String, Object> config = (Map) getServletContext().getAttribute(Constants.CONFIG);
      config.put(Constants.CSS_THEME, theme);
    }

    try {
      chain.doFilter(request, response);
    } finally {
      LocaleContextHolder.setLocaleContext(previousLocaleContext);
    }
  }

  private Locale resolveLocale(HttpServletRequest request) {
    String requestPath = request.getRequestURI().substring(request.getContextPath().length());
    if ((requestPath.equals("/api/v1") || requestPath.startsWith("/api/v1/"))
        && request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) != null) {
      return request.getLocale();
    }
    return userLocaleService.getLocale();
  }
}
