package com.axiope.webapp.taglib;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.text.StringEscapeUtils;

/**
 * Emits the Vite HMR runtime ({@code @vite/client}) into the page head when {@code reactDevMode} is
 * enabled, together with the React Fast Refresh preamble required by
 * {@code @vitejs/plugin-react-swc}. In production the tag is a no-op.
 *
 * <p>Intended for placement inside the {@code <head>} of layout decorators so that every decorated
 * page participates in HMR — including pages that load bundles dynamically (e.g. TinyMCE plugin
 * dialogs via {@code viteBundleLoader.js}). Shares request-scoped dedupe with {@link BundleTag} so
 * the script tag is written at most once per request even when {@code <rst:bundle>} would also have
 * emitted it.
 */
public class ViteClientTag extends TagSupport {

  private static final long serialVersionUID = 1L;
  private static final String CLIENT_URL = BundleTag.DIST_PUBLIC_PATH + BundleTag.VITE_CLIENT_PATH;
  private static final String DEDUPE_KEY = "script:module:" + CLIENT_URL;

  @Override
  public int doStartTag() throws JspException {
    if (!isHmrEnabled()) {
      return TagSupport.SKIP_BODY;
    }

    try {
      if (getRenderedAssetKeys().add(BundleTag.REACT_PREAMBLE_DEDUPE_KEY)) {
        pageContext.getOut().write(BundleTag.reactRefreshPreambleHtml());
      }
      if (getRenderedAssetKeys().add(DEDUPE_KEY)) {
        pageContext
            .getOut()
            .write(
                "<script type=\"module\" src=\""
                    + StringEscapeUtils.escapeHtml4(CLIENT_URL)
                    + "\"></script>");
      }
    } catch (IOException e) {
      throw new JspException("Failed to render Vite client tag", e);
    }
    return TagSupport.SKIP_BODY;
  }

  boolean isHmrEnabled() {
    return FrontendCacheVersion.isReactDevMode(pageContext.getServletContext());
  }

  @SuppressWarnings("unchecked")
  Set<String> getRenderedAssetKeys() {
    HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
    Object existing = request.getAttribute(BundleTag.RENDERED_ASSETS_ATTR);
    if (existing instanceof Set) {
      return (Set<String>) existing;
    }
    Set<String> renderedAssets = new LinkedHashSet<>();
    request.setAttribute(BundleTag.RENDERED_ASSETS_ATTR, renderedAssets);
    return renderedAssets;
  }
}
