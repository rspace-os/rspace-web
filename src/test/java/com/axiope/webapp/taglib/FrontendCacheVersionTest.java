package com.axiope.webapp.taglib;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;

public class FrontendCacheVersionTest {

  @Test
  public void cachesReactDevModeLookupInServletContext() {
    ServletContext servletContext = new MockServletContext();
    WebApplicationContext applicationContext = Mockito.mock(WebApplicationContext.class);
    Environment environment = Mockito.mock(Environment.class);
    when(applicationContext.getEnvironment()).thenReturn(environment);
    when(environment.getProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY)).thenReturn("true");
    servletContext.setAttribute(
        WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);

    assertTrue(FrontendCacheVersion.isReactDevMode(servletContext));
    assertTrue(FrontendCacheVersion.isReactDevMode(servletContext));
    assertTrue(
        Boolean.TRUE.equals(
            servletContext.getAttribute(FrontendCacheVersion.REACT_DEV_MODE_CACHE_ATTR)));
    verify(environment).getProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY);
  }

  @Test
  public void returnsCachedReactDevModeWithoutRecheckingSpringEnvironment() {
    ServletContext servletContext = new MockServletContext();
    WebApplicationContext applicationContext = Mockito.mock(WebApplicationContext.class);
    Environment environment = Mockito.mock(Environment.class);
    servletContext.setAttribute(FrontendCacheVersion.REACT_DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    servletContext.setAttribute(
        WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);

    assertFalse(FrontendCacheVersion.isReactDevMode(servletContext));
    Mockito.verifyNoInteractions(applicationContext, environment);
  }

  @Test
  public void devModeFallsBackToSystemPropertyWithoutSpringContext() {
    ServletContext servletContext = new MockServletContext();
    assertFalse(FrontendCacheVersion.isDevMode(servletContext));

    System.setProperty(FrontendCacheVersion.FRONTEND_DEV_MODE_PROPERTY, "true");
    try {
      assertTrue(FrontendCacheVersion.isDevMode(servletContext));
    } finally {
      System.clearProperty(FrontendCacheVersion.FRONTEND_DEV_MODE_PROPERTY);
    }
  }

  @Test
  public void devModeFollowsPropertiesNotJustRunProfile() {
    MockEnvironment prod = new MockEnvironment();
    prod.setActiveProfiles("prod");
    assertFalse(FrontendCacheVersion.isDevMode(prod));

    prod.setProperty(FrontendCacheVersion.FRONTEND_DEV_MODE_PROPERTY, "true");
    assertTrue(FrontendCacheVersion.isDevMode(prod));

    MockEnvironment prodWithHmr = new MockEnvironment();
    prodWithHmr.setActiveProfiles("prod");
    prodWithHmr.setProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY, "true");
    assertTrue(FrontendCacheVersion.isDevMode(prodWithHmr));

    MockEnvironment run = new MockEnvironment();
    run.setActiveProfiles("run");
    assertTrue(FrontendCacheVersion.isDevMode(run));
  }
}
