package com.axiope.webapp.taglib;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

/**
 * Emits a context-relative URL for a static asset (under {@code /scripts/}, {@code /styles/},
 * etc.), appending the shared cache-busting {@code ?v=<token>} query parameter. In dev mode, legacy
 * {@code /scripts/} and {@code /styles/} assets can opt out via {@link
 * FrontendCacheVersion#LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY}; production always appends
 * the token. Drop-in replacement for {@code <c:url value="..."/>} when referencing static assets
 * that need to be invalidated together with the rest of the frontend bundle.
 *
 * <p>The token is resolved by {@link FrontendCacheVersion#resolve}: a per-request UUID in dev mode,
 * or the RSpace version string in production.
 */
@Setter
public class AssetUrlTag extends TagSupport {

  private static final long serialVersionUID = 1L;
  static final String PRODUCTION_URL_CACHE_ATTR =
      AssetUrlTag.class.getName() + ".PRODUCTION_URL_CACHE";

  private String value;

  @Override
  public int doStartTag() throws JspException {
    if (StringUtils.isBlank(value)) {
      throw new IllegalStateException("value attribute must be provided");
    }

    String url = buildUrl();
    try {
      pageContext.getOut().write(StringEscapeUtils.escapeHtml4(url));
    } catch (IOException e) {
      throw new JspException("Failed to write asset URL", e);
    }
    return SKIP_BODY;
  }

  String buildUrl() {
    HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
    String resolved =
        value.startsWith("/") ? StringUtils.defaultString(request.getContextPath()) + value : value;
    boolean devMode = FrontendCacheVersion.isDevMode(pageContext.getServletContext());
    if (devMode
        && isLegacyAsset(value)
        && !FrontendCacheVersion.isLegacyAssetCacheBustingEnabledInDevMode(
            pageContext.getServletContext())) {
      return resolved;
    }
    String version =
        FrontendCacheVersion.resolve(pageContext.getServletContext(), request, devMode);
    if (StringUtils.isBlank(version)) {
      return resolved;
    }
    if (devMode) {
      return appendVersion(resolved, version);
    }
    String cacheKey = resolved + "\n" + version;
    return getProductionUrlCache()
        .computeIfAbsent(cacheKey, key -> appendVersion(resolved, version));
  }

  private Map<String, String> getProductionUrlCache() {
    ServletContext servletContext = pageContext.getServletContext();
    Object existing = servletContext.getAttribute(PRODUCTION_URL_CACHE_ATTR);
    if (existing instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> cached = (Map<String, String>) existing;
      return cached;
    }
    Map<String, String> created = new ConcurrentHashMap<>();
    servletContext.setAttribute(PRODUCTION_URL_CACHE_ATTR, created);
    return created;
  }

  private String appendVersion(String resolved, String version) {
    char separator = resolved.indexOf('?') >= 0 ? '&' : '?';
    return resolved + separator + "v=" + version;
  }

  private boolean isLegacyAsset(String assetPath) {
    String pathWithoutQuery =
        StringUtils.substringBefore(StringUtils.defaultString(assetPath), "?");
    return pathWithoutQuery.startsWith("/scripts/")
        || pathWithoutQuery.startsWith("scripts/")
        || pathWithoutQuery.startsWith("/styles/")
        || pathWithoutQuery.startsWith("styles/");
  }
}
