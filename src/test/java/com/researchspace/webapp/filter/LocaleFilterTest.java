package com.researchspace.webapp.filter;

import com.researchspace.Constants;
import com.researchspace.service.UserLocaleService;
import java.util.Locale;
import javax.servlet.ServletException;
import javax.servlet.jsp.jstl.core.Config;
import junit.framework.TestCase;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;

public class LocaleFilterTest extends TestCase {
  private LocaleFilter filter = null;
  private static final Locale EN_US = Locale.forLanguageTag("en-US");

  protected void setUp() throws Exception {
    LocaleContextHolder.resetLocaleContext();

    MockServletContext servletContext = new MockServletContext();
    StaticWebApplicationContext appContext = new StaticWebApplicationContext();
    appContext.setServletContext(servletContext);
    appContext.registerSingleton("userLocaleService", UserLocaleService.class);
    appContext.refresh();
    servletContext.setAttribute(
        WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appContext);

    filter = new LocaleFilter();
    filter.init(new MockFilterConfig(servletContext));
  }

  protected void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  public void testSetLocaleInSessionWhenSessionIsNull() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());

    assertNull(request.getSession().getAttribute(Constants.PREFERRED_LOCALE_KEY));
    assertEquals("en-US", request.getAttribute(LocaleFilter.RESOLVED_LOCALE_TAG_REQUEST_ATTRIBUTE));
    assertNull(LocaleContextHolder.getLocaleContext());
  }

  public void testSetLocaleInSessionWhenSessionNotNull() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();

    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setSession(new MockHttpSession(null));

    filter.doFilter(request, response, new MockFilterChain());

    Locale locale = (Locale) request.getSession().getAttribute(Constants.PREFERRED_LOCALE_KEY);
    assertEquals(EN_US, locale);
    assertEquals("en-US", request.getAttribute(LocaleFilter.RESOLVED_LOCALE_TAG_REQUEST_ATTRIBUTE));
    assertNull(LocaleContextHolder.getLocaleContext());
  }

  public void testLocaleParamIsIgnored() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addParameter("locale", "es");
    request.setSession(new MockHttpSession(null));

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());

    Locale locale = (Locale) request.getSession().getAttribute(Constants.PREFERRED_LOCALE_KEY);
    assertEquals(EN_US, locale);
  }

  public void testApiAcceptLanguageCannotSelectAnUnavailableLocale() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/documents");
    request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR, en-US;q=0.8");

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (wrappedRequest, response) -> {
          assertEquals(EN_US, wrappedRequest.getLocale());
          assertEquals(EN_US, LocaleContextHolder.getLocale());
        });

    assertNull(LocaleContextHolder.getLocaleContext());
  }

  public void testNonApiAcceptLanguageDoesNotOverrideDeploymentLocale() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/workspace");
    request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR");

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (wrappedRequest, response) -> assertEquals(EN_US, wrappedRequest.getLocale()));
  }

  public void testJstlLocaleIsSet() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();

    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setSession(new MockHttpSession(null));

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(EN_US, Config.get(request.getSession(), Config.FMT_LOCALE));
  }

  public void testLocaleContextIsRestoredWhenChainThrows() throws Exception {
    Locale originalLocale = Locale.GERMAN;
    LocaleContextHolder.setLocale(originalLocale);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setSession(new MockHttpSession(null));
    MockHttpServletResponse response = new MockHttpServletResponse();

    try {
      filter.doFilter(
          request,
          response,
          (servletRequest, servletResponse) -> {
            throw new ServletException("boom");
          });
      fail("Expected ServletException");
    } catch (ServletException expected) {
      assertEquals("boom", expected.getMessage());
    }

    assertEquals("en-US", request.getAttribute(LocaleFilter.RESOLVED_LOCALE_TAG_REQUEST_ATTRIBUTE));
    assertEquals(originalLocale, LocaleContextHolder.getLocale());
  }
}
