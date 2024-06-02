package com.axiope.webapp.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.ContextLoaderListener;

/**
 * This class tests the StartupListener class to verify that variables are placed into the servlet
 * context.
 */
public class StartupListenerTest extends SpringTransactionalTest {
  private MockServletContext sc = null;
  private ServletContextListener listener = null;
  private ContextLoaderListener springListener = null;

  class StartupListenerTss extends StartupListener {
    ApplicationContext getApplicationContext(ServletContext context) {
      return applicationContext;
    }
  }

  @Before
  public void setUp() throws Exception {

    sc = new MockServletContext("");
    sc.addInitParameter(Constants.CSS_THEME, "simplicity");

    listener = new StartupListenerTss();
  }

  public void tearDown() throws Exception {
    super.tearDown();
    springListener = null;
    listener = null;
    sc = null;
  }

  @Test
  public void testContextInitialized() {
    listener.contextInitialized(new ServletContextEvent(sc));

    assertTrue(sc.getAttribute(Constants.CONFIG) != null);
    Map config = (Map) sc.getAttribute(Constants.CONFIG);
    assertEquals(config.get(Constants.CSS_THEME), "simplicity");

    assertTrue(sc.getAttribute(StartupListener.RS_DEPLOY_PROPS_CTX_ATTR_NAME) != null);
  }
}
