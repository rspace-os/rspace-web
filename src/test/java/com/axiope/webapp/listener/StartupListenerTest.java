package com.axiope.webapp.listener;

import static org.junit.Assert.*;

import com.researchspace.Constants;
import com.researchspace.testutils.SpringTransactionalTest;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockServletContext;

/**
 * This class tests the StartupListener class to verify that variables are placed into the servlet
 * context.
 */
public class StartupListenerTest extends SpringTransactionalTest {
  private MockServletContext sc = null;
  private ServletContextListener listener = null;
  private boolean bundleManifestPreWarmed;

  class StartupListenerTss extends StartupListener {
    ApplicationContext getApplicationContext(ServletContext context) {
      return applicationContext;
    }

    @Override
    void preWarmBundleManifestCache(ApplicationContext applicationContext, ServletContext context) {
      bundleManifestPreWarmed = true;
    }

    @Override
    void registerViteDevServerProxyIfEnabled(
        ApplicationContext applicationContext, ServletContext context) {
      // No-op in tests — the proxy servlet is dev-only and not under test here.
    }
  }

  @Before
  public void setUp() throws Exception {

    sc = new MockServletContext("");
    sc.addInitParameter(Constants.CSS_THEME, "simplicity");
    bundleManifestPreWarmed = false;

    listener = new StartupListenerTss();
  }

  public void tearDown() throws Exception {
    super.tearDown();
    listener = null;
    sc = null;
  }

  @Test
  public void testContextInitialized() {
    listener.contextInitialized(new ServletContextEvent(sc));

    assertNotNull(sc.getAttribute(Constants.CONFIG));
    Map config = (Map) sc.getAttribute(Constants.CONFIG);
    assertNotNull(config);
    assertEquals("simplicity", config.get(Constants.CSS_THEME));

    assertNotNull(sc.getAttribute(StartupListener.RS_DEPLOY_PROPS_CTX_ATTR_NAME));
    assertTrue(bundleManifestPreWarmed);
  }
}
