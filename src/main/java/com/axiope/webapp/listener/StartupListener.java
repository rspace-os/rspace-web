package com.axiope.webapp.listener;

import com.axiope.webapp.dev.ViteDevServerProxyServlet;
import com.axiope.webapp.taglib.BundleTag;
import com.axiope.webapp.taglib.FrontendCacheVersion;
import com.researchspace.Constants;
import com.researchspace.properties.IPropertyHolder;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
    preWarmBundleManifestCache(ctx, context);
    initCacheVersion(ctx, propHolder, context);
    registerViteDevServerProxyIfEnabled(ctx, context);
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

  void preWarmBundleManifestCache(ApplicationContext applicationContext, ServletContext context) {
    boolean isDevMode = applicationContext.getEnvironment().acceptsProfiles(Profiles.of("run"));
    BundleTag.preWarmManifestCache(context, isDevMode);
  }

  /**
   * Stores the cache-busting version token in the servlet context as {@link
   * FrontendCacheVersion#CACHE_VERSION_ATTR}.
   *
   * <ul>
   *   <li><b>Dev ({@code run} profile):</b> nothing is stored here; {@link FrontendCacheVersion}
   *       generates a fresh UUID per request so every page hit forces browsers to re-fetch assets.
   *   <li><b>Production:</b> the RSpace application version string (e.g. {@code 2.23.0}) so
   *       browsers cache assets across requests within the same deployment and bust the cache on
   *       upgrade.
   * </ul>
   */
  void initCacheVersion(
      ApplicationContext applicationContext, IPropertyHolder propHolder, ServletContext context) {
    boolean isDevMode = applicationContext.getEnvironment().acceptsProfiles(Profiles.of("run"));
    if (!isDevMode) {
      String cacheVersion = propHolder.getVersionMessage();
      log.info("Production mode: using RSpace version '{}' as cache-buster token", cacheVersion);
      context.setAttribute(FrontendCacheVersion.CACHE_VERSION_ATTR, cacheVersion);
    }
    // In dev mode, FrontendCacheVersion generates a fresh UUID per request.
  }

  /**
   * Registers a same-origin reverse proxy from {@code /ui/dist/*} to the local Vite dev server when
   * {@code reactDevMode=true}. Lets JSPs emit relative asset URLs in both dev and production
   * builds, removing the cross-origin asset load that the previous Vite integration required.
   */
  void registerViteDevServerProxyIfEnabled(
      ApplicationContext applicationContext, ServletContext context) {
    Environment environment = applicationContext.getEnvironment();
    if (!Boolean.parseBoolean(
        StringUtils.trimToEmpty(
            environment.getProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY)))) {
      return;
    }
    String configured =
        StringUtils.trimToEmpty(environment.getProperty("ui.vite.devServer.origin"));
    String origin = configured.isEmpty() ? ViteDevServerProxyServlet.DEFAULT_ORIGIN : configured;
    if (configured.isEmpty()) {
      log.info(
          "reactDevMode=true, ui.vite.devServer.origin unset — using default origin {}", origin);
    }
    try {
      ServletRegistration.Dynamic registration =
          context.addServlet("viteDevServerProxy", new ViteDevServerProxyServlet(origin));
      if (registration == null) {
        log.warn(
            "Vite dev server proxy was already registered; skipping (origin would have been {})",
            origin);
        return;
      }
      registration.addMapping("/ui/dist/*");
      log.info("Registered Vite dev server proxy at /ui/dist/* -> {}", origin);
    } catch (IllegalStateException e) {
      log.warn(
          "Unable to register Vite dev server proxy at /ui/dist/*: {} (context already started?)",
          e.getMessage());
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
