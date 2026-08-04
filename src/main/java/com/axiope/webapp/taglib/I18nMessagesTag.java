package com.axiope.webapp.taglib;

import com.researchspace.service.UserLocaleService;
import jakarta.servlet.jsp.JspException;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * Loads the legacy JavaScript message catalogue as two blocking scripts: the messages for the
 * deployment's locale, and the Vite-built code that gives {@code RS.msg} its ICU formatting.
 *
 * <p>Both must precede the first {@code RS.msg} call, so this tag belongs in {@code <head>} and
 * neither script may be deferred. Their relative order does not matter.
 */
public class I18nMessagesTag extends BundleTag {

  private static final long serialVersionUID = 1L;

  static final Locale FALLBACK_LOCALE = Locale.forLanguageTag("en-US");

  public I18nMessagesTag() {
    setBundle("legacyI18n");
  }

  @Override
  void renderBundle() throws JspException {
    renderClassicScriptTag(catalogueUrl());
    super.renderBundle();
  }

  /** The file name carries the locale; the shared cache-busting token stands in for a hash. */
  String catalogueUrl() {
    String url =
        StringUtils.defaultString(getRequest().getContextPath())
            + DIST_PUBLIC_PATH
            + "legacyMessages."
            + resolveLocale().toLanguageTag()
            + ".js";
    String version =
        FrontendCacheVersion.resolve(pageContext.getServletContext(), getRequest(), isDevMode());
    return StringUtils.isBlank(version) ? url : url + "?v=" + version;
  }

  Locale resolveLocale() {
    WebApplicationContext context =
        WebApplicationContextUtils.getWebApplicationContext(pageContext.getServletContext());
    return context == null ? FALLBACK_LOCALE : context.getBean(UserLocaleService.class).getLocale();
  }

  @Override
  boolean usesModuleScripts() {
    return false;
  }
}
