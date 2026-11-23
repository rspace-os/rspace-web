package com.axiope.webapp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axiope.webapp.taglib.FrontendCacheVersion;
import com.researchspace.properties.IPropertyHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;

/** Dev mode decided at startup must follow the properties, not only the {@code run} profile. */
@ExtendWith(MockitoExtension.class)
public class StartupListenerFrontendDevModeTest {

  @Mock private ApplicationContext applicationContext;
  @Mock private IPropertyHolder propHolder;

  private MockEnvironment environment;
  private MockServletContext servletContext;
  private StartupListener listener;

  @BeforeEach
  public void setUp() {
    environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    when(applicationContext.getEnvironment()).thenReturn(environment);
    servletContext = new MockServletContext();
    listener = new StartupListener();
  }

  private void startUp() {
    listener.preWarmBundleManifestCache(applicationContext, servletContext);
    listener.initCacheVersion(applicationContext, propHolder, servletContext);
  }

  @Test
  public void prodAloneUsesVersionToken() {
    when(propHolder.getVersionMessage()).thenReturn("2.99.0");

    startUp();

    assertFalse(FrontendCacheVersion.isDevMode(servletContext));
    assertEquals("2.99.0", servletContext.getAttribute(FrontendCacheVersion.CACHE_VERSION_ATTR));
  }

  @Test
  public void prodWithHmrIsDevMode() {
    environment.setProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY, "true");

    startUp();

    assertDevModeWithoutVersionToken();
  }

  @Test
  public void prodWithFrontendDevModeIsDevMode() {
    environment.setProperty(FrontendCacheVersion.FRONTEND_DEV_MODE_PROPERTY, "true");

    startUp();

    assertDevModeWithoutVersionToken();
  }

  private void assertDevModeWithoutVersionToken() {
    assertTrue(FrontendCacheVersion.isDevMode(servletContext));
    assertNull(servletContext.getAttribute(FrontendCacheVersion.CACHE_VERSION_ATTR));
    verifyNoInteractions(propHolder);
  }
}
