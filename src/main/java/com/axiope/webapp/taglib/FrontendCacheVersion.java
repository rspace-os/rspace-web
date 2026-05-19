package com.axiope.webapp.taglib;

import java.util.UUID;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * Shared cache-busting state for the JSP tags that emit static-asset URLs ({@link BundleTag} for
 * Vite-built bundles under {@code /ui/dist/}, {@link AssetUrlTag} for legacy assets under {@code
 * /scripts/} and {@code /styles/}). Centralises the dev-mode detection and the version token so
 * asset URLs that participate in cache busting within a single request share the same {@code
 * ?v=<token>} value.
 *
 * <p>Token resolution:
 *
 * <ul>
 *   <li><b>Dev:</b> a fresh UUID is generated per request and cached as a request attribute, so
 *       browsers re-fetch assets on every page hit after a rebuild.
 *   <li><b>Production:</b> the RSpace version string stored in the servlet context at startup by
 *       {@link com.axiope.webapp.listener.StartupListener}.
 * </ul>
 */
public final class FrontendCacheVersion {

  /** Servlet context attribute holding the production cache-busting token. */
  public static final String CACHE_VERSION_ATTR =
      FrontendCacheVersion.class.getName() + ".CACHE_VERSION";

  /** Servlet context attribute caching the dev-mode flag computed once at startup. */
  public static final String DEV_MODE_CACHE_ATTR =
      FrontendCacheVersion.class.getName() + ".DEV_MODE";

  /** Spring environment / system property that toggles Vite HMR. */
  public static final String REACT_DEV_MODE_PROPERTY = "reactDevMode";

  /**
   * Spring environment / system property that re-enables cache busting for legacy {@code /scripts/}
   * and {@code /styles/} assets during dev mode. Defaults to {@code false}; production ignores this
   * flag and always cache-busts legacy assets.
   */
  public static final String LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY =
      "legacyAssetCacheBustingInDevMode";

  private static final String REQUEST_TOKEN_ATTR = CACHE_VERSION_ATTR + ".REQUEST";

  private FrontendCacheVersion() {}

  /**
   * Returns the cache-busting version token to append to a static-asset URL. Within a single
   * request, repeated calls return the same value.
   */
  public static String resolve(ServletContext servletContext, HttpServletRequest request) {
    return resolve(servletContext, request, isDevMode(servletContext));
  }

  /**
   * Variant of {@link #resolve(ServletContext, HttpServletRequest)} that uses a pre-computed
   * dev-mode flag — lets callers that already have a (possibly overridden) dev-mode check feed it
   * in rather than re-running the servlet-context lookup.
   */
  public static String resolve(
      ServletContext servletContext, HttpServletRequest request, boolean devMode) {
    if (devMode) {
      Object existing = request.getAttribute(REQUEST_TOKEN_ATTR);
      if (existing instanceof String) {
        return (String) existing;
      }
      String uuid = UUID.randomUUID().toString();
      request.setAttribute(REQUEST_TOKEN_ATTR, uuid);
      return uuid;
    }
    Object value = servletContext.getAttribute(CACHE_VERSION_ATTR);
    return value instanceof String ? (String) value : null;
  }

  /**
   * Returns {@code true} when the application is running in dev mode (Spring {@code run} profile or
   * HMR enabled). The result is cached in the servlet context after the first lookup.
   */
  public static boolean isDevMode(ServletContext servletContext) {
    Object cachedValue = servletContext.getAttribute(DEV_MODE_CACHE_ATTR);
    if (cachedValue instanceof Boolean) {
      return (Boolean) cachedValue;
    }

    if (isReactDevMode(servletContext)) {
      servletContext.setAttribute(DEV_MODE_CACHE_ATTR, Boolean.TRUE);
      return true;
    }

    WebApplicationContext applicationContext =
        WebApplicationContextUtils.getWebApplicationContext(servletContext);
    if (applicationContext == null) {
      return false;
    }

    Environment environment = applicationContext.getEnvironment();
    boolean devMode = environment.acceptsProfiles(Profiles.of("run"));
    servletContext.setAttribute(DEV_MODE_CACHE_ATTR, devMode);
    return devMode;
  }

  /** Returns {@code true} when Vite HMR is enabled via the {@code reactDevMode} property. */
  public static boolean isReactDevMode(ServletContext servletContext) {
    WebApplicationContext applicationContext =
        WebApplicationContextUtils.getWebApplicationContext(servletContext);
    if (applicationContext != null) {
      return Boolean.parseBoolean(
          StringUtils.trimToEmpty(
              applicationContext.getEnvironment().getProperty(REACT_DEV_MODE_PROPERTY)));
    }
    return Boolean.getBoolean(REACT_DEV_MODE_PROPERTY);
  }

  /**
   * Returns {@code true} when legacy {@code /scripts/} and {@code /styles/} assets should keep
   * their {@code ?v=<token>} suffix in dev mode. Production callers should ignore this and always
   * cache-bust.
   */
  public static boolean isLegacyAssetCacheBustingEnabledInDevMode(ServletContext servletContext) {
    WebApplicationContext applicationContext =
        WebApplicationContextUtils.getWebApplicationContext(servletContext);
    if (applicationContext != null) {
      return Boolean.parseBoolean(
          StringUtils.trimToEmpty(
              applicationContext
                  .getEnvironment()
                  .getProperty(LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY)));
    }
    return Boolean.getBoolean(LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY);
  }

  /**
   * Stores the dev-mode flag computed at startup so later lookups skip the Spring profile check.
   */
  public static void rememberDevMode(ServletContext servletContext, boolean devMode) {
    servletContext.setAttribute(DEV_MODE_CACHE_ATTR, devMode);
  }
}
