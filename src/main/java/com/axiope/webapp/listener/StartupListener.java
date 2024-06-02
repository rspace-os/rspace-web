package com.axiope.webapp.listener;

import com.researchspace.Constants;
import com.researchspace.properties.IPropertyHolder;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * StartupListener class used to initialize and database settings and populate any application-wide
 * drop-downs.
 *
 * <p>This class is managed by the web container, <b>NOT</b> Spring, so we can't autowire or inject
 * dependencies here.
 */
public class StartupListener implements ServletContextListener {
  public static final String RS_DEPLOY_PROPS_CTX_ATTR_NAME = "RS_DEPLOY_PROPS";
  private static final Logger log = LoggerFactory.getLogger(StartupListener.class);

  /** {@inheritDoc} */
  @SuppressWarnings("unchecked")
  public void contextInitialized(ServletContextEvent event) {
    log.debug("Initializing context...");
    ServletContext context = event.getServletContext();
    // check if the config
    // object already exists
    Map<String, Object> config = (HashMap<String, Object>) context.getAttribute(Constants.CONFIG);
    if (config == null) {
      config = new HashMap<String, Object>();
    }
    if (context.getInitParameter(Constants.CSS_THEME) != null) {
      config.put(Constants.CSS_THEME, context.getInitParameter(Constants.CSS_THEME));
    }
    ApplicationContext ctx = getApplicationContext(context);
    IPropertyHolder propHolder = (IPropertyHolder) ctx.getBean(IPropertyHolder.class);
    context.setAttribute(Constants.CONFIG, config);

    setupContext(context);
    getProperties(propHolder, context);
  }

  ApplicationContext getApplicationContext(ServletContext context) {
    return WebApplicationContextUtils.getRequiredWebApplicationContext(context);
  }

  /**
   * This method uses the LookupManager to lookup available roles from the data layer.
   *
   * @param context The servlet context
   */
  public void setupContext(ServletContext context) {
    // ApplicationContext ctx = getApplicationContext(context);  ??? ctx not used
    getApplicationContext(context);
  }

  /*
   * Loads property file into a servlet context attribute, so that its properties are available
   * directly to JSPs.
   */
  void getProperties(IPropertyHolder props, ServletContext context) {
    if (props != null) {
      if (context.getAttribute(RS_DEPLOY_PROPS_CTX_ATTR_NAME) == null) {
        context.setAttribute(RS_DEPLOY_PROPS_CTX_ATTR_NAME, props);
      }
    }
  }

  /**
   * Shutdown servlet context (currently a no-op method).
   *
   * @param servletContextEvent The servlet context event
   */
  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    // add resource cleanup stuff here for when server is shutdown
  }
}
