package com.researchspace.webapp.filter;

import com.researchspace.Constants;
import com.researchspace.service.UserLocaleService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.jstl.core.Config;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
  public void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    String requestPath = contextRelativePath(request);
    Locale locale = resolveLocale(request, requestPath);
    LocaleContext previousLocaleContext = LocaleContextHolder.getLocaleContext();
    LocaleContextHolder.setLocale(locale);
    request.setAttribute(RESOLVED_LOCALE_TAG_REQUEST_ATTRIBUTE, locale.toLanguageTag());
    if (isApiV2Request(requestPath)) {
      response.setHeader(HttpHeaders.CONTENT_LANGUAGE, locale.toLanguageTag());
      response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
    }

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
      Object configAttribute = getServletContext().getAttribute(Constants.CONFIG);
      if (!(configAttribute instanceof Map<?, ?> existing)) {
        throw new IllegalStateException("Application config attribute must be a map");
      }
      Map<String, Object> config = new LinkedHashMap<>();
      existing.forEach((key, value) -> config.put(String.class.cast(key), value));
      config.put(Constants.CSS_THEME, theme);
      getServletContext().setAttribute(Constants.CONFIG, config);
    }

    try {
      chain.doFilter(request, response);
    } finally {
      LocaleContextHolder.setLocaleContext(previousLocaleContext);
    }
  }

  private Locale resolveLocale(HttpServletRequest request, String requestPath) {
    Locale configuredLocale = userLocaleService.getLocale();
    if ((isApiRequest(requestPath, "/api/v1") || isApiRequest(requestPath, "/api/v2"))
        && request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) != null) {
      Locale requestedLocale = request.getLocale();
      if (configuredLocale.equals(requestedLocale)) {
        return requestedLocale;
      }
    }
    return configuredLocale;
  }

  private static String contextRelativePath(HttpServletRequest request) {
    return request.getRequestURI().substring(request.getContextPath().length());
  }

  private static boolean isApiV2Request(String requestPath) {
    return isApiRequest(requestPath, "/api/v2");
  }

  private static boolean isApiRequest(String requestPath, String prefix) {
    return requestPath.equals(prefix) || requestPath.startsWith(prefix + "/");
  }
}
