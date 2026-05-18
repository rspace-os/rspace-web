package com.axiope.webapp.listener;

import com.axiope.webapp.dev.ViteDevServerProxyServlet;
import com.axiope.webapp.taglib.BundleTag;
import com.researchspace.Constants;
import com.researchspace.properties.IPropertyHolder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    initCacheVersion(propHolder, context);
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
   * BundleTag#CACHE_VERSION_ATTR}.
   *
   * <p>The token is a SHA-256 content hash computed over:
   *
   * <ol>
   *   <li>All legacy static files listed in {@code /build-resources/resources_to_MD5_rename.txt}
   *       (JS, CSS, TinyMCE plugins, etc. that are served outside of the Vite pipeline).
   *   <li>The Vite build manifest ({@code /ui/dist/.vite/manifest.json}), representing the current
   *       state of Vite-bundled assets.
   * </ol>
   *
   * <p>Because the token is derived from actual file contents, it changes automatically whenever
   * any static asset changes — regardless of whether the RSpace version string has been bumped.
   * This is correct for both development restarts and production hotfix deployments. If no files
   * can be read (e.g. a fresh checkout with no built UI), the RSpace version string is used as a
   * fallback so the server still starts.
   */
  void initCacheVersion(IPropertyHolder propHolder, ServletContext context) {
    String hash = computeAssetHash(context);
    String cacheVersion;
    if (StringUtils.isNotBlank(hash)) {
      cacheVersion = hash;
      log.info("Cache-buster version token set from asset content hash: '{}'", cacheVersion);
    } else {
      cacheVersion = propHolder.getVersionMessage();
      log.warn(
          "No static assets found to hash; falling back to version string '{}' as cache-buster"
              + " token",
          cacheVersion);
    }
    context.setAttribute(BundleTag.CACHE_VERSION_ATTR, cacheVersion);
  }

  /**
   * Computes a short hex token by SHA-256-hashing the contents of all legacy static files and the
   * Vite manifest, in a deterministic order. Returns the first 16 hex characters of the digest, or
   * an empty string if no readable files were found.
   */
  static String computeAssetHash(ServletContext servletContext) {
    List<String> webPaths = new ArrayList<>();

    // Legacy static files (JS, CSS, TinyMCE plugins, …).
    String listResource = "/build-resources/resources_to_MD5_rename.txt";
    try (InputStream listStream = servletContext.getResourceAsStream(listResource)) {
      if (listStream != null) {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(listStream, StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
              webPaths.add("/" + line);
            }
          }
        }
      }
    } catch (IOException e) {
      log.warn("Failed to read legacy asset list from {}", listResource, e);
    }

    // Vite manifest — represents the state of all React bundles.
    webPaths.add(BundleTag.VITE_MANIFEST_PATH);

    // Sort for determinism across JVM restarts / OS platforms.
    Collections.sort(webPaths);

    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      log.error("SHA-256 not available; cache version hash cannot be computed", e);
      return "";
    }

    boolean anyFileRead = false;
    for (String webPath : webPaths) {
      try (InputStream resource = servletContext.getResourceAsStream(webPath)) {
        if (resource == null) {
          continue;
        }
        // Feed the path itself into the digest so renames are detected even if content is stable.
        digest.update(webPath.getBytes(StandardCharsets.UTF_8));
        try (DigestInputStream dis = new DigestInputStream(resource, digest)) {
          byte[] buf = new byte[8192];
          //noinspection StatementWithEmptyBody
          while (dis.read(buf) != -1) {}
        }
        anyFileRead = true;
      } catch (IOException e) {
        log.warn("Failed to hash static asset {}", webPath, e);
      }
    }

    if (!anyFileRead) {
      return "";
    }

    byte[] digestBytes = digest.digest();
    StringBuilder sb = new StringBuilder(32);
    for (byte b : digestBytes) {
      sb.append(String.format("%02x", b));
    }
    // 16 hex chars (64 bits) is ample for a cache-buster token.
    return sb.substring(0, 16);
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
        StringUtils.trimToEmpty(environment.getProperty(BundleTag.REACT_DEV_MODE_PROPERTY)))) {
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
