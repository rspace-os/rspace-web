package com.axiope.webapp.taglib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockJspWriter;
import org.springframework.mock.web.MockServletContext;

@ExtendWith(MockitoExtension.class)
public class AssetUrlTagTest {

  @Mock private PageContext pageContext;

  private final StringWriter output = new StringWriter();
  private MockHttpServletRequest request;
  private MockServletContext servletContext;
  private JspWriter writer;
  private AssetUrlTag tag;

  @BeforeEach
  public void setUp() {
    tag = new AssetUrlTag();
    output.getBuffer().setLength(0);
    servletContext = new MockServletContext();
    request = new MockHttpServletRequest(servletContext);
    writer = new MockJspWriter(output);
    System.clearProperty(FrontendCacheVersion.LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY);
    tag.setPageContext(pageContext);
  }

  private void stubPageContext() {
    when(pageContext.getRequest()).thenReturn(request);
    when(pageContext.getOut()).thenReturn(writer);
    when(pageContext.getServletContext()).thenReturn(servletContext);
  }

  @Test
  public void appendsVersionTokenInProductionMode() throws JspException {
    stubPageContext();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    servletContext.setAttribute(FrontendCacheVersion.CACHE_VERSION_ATTR, "2.23.0");
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("/scripts/global.js?v=2.23.0", output.toString());
  }

  @Test
  public void cachesResolvedUrlsInProductionMode() throws JspException {
    stubPageContext();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    servletContext.setAttribute(FrontendCacheVersion.CACHE_VERSION_ATTR, "2.23.0");
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    @SuppressWarnings("unchecked")
    Map<String, String> cache =
        (Map<String, String>) servletContext.getAttribute(AssetUrlTag.PRODUCTION_URL_CACHE_ATTR);
    assertEquals(1, cache.size());
    assertTrue(cache.containsValue("/scripts/global.js?v=2.23.0"));

    output.getBuffer().setLength(0);
    AssetUrlTag second = new AssetUrlTag();
    second.setPageContext(pageContext);
    second.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, second.doStartTag());
    assertEquals("/scripts/global.js?v=2.23.0", output.toString());
    assertEquals(1, cache.size());
  }

  @Test
  public void doesNotPopulateProductionCacheInDevMode() throws JspException {
    stubPageContext();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);
    System.setProperty(
        FrontendCacheVersion.LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY, "true");
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertFalse(
        servletContext.getAttribute(AssetUrlTag.PRODUCTION_URL_CACHE_ATTR) instanceof Map<?, ?>);
  }

  @Test
  public void omitsVersionTokenForLegacyScriptsInDevModeByDefault() throws JspException {
    stubPageContext();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("/scripts/global.js", output.toString());
  }

  @Test
  public void usesPerRequestUuidForLegacyScriptsInDevModeWhenEnabled() throws JspException {
    stubPageContext();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);
    System.setProperty(
        FrontendCacheVersion.LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY, "true");
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    String first = output.toString();
    assertTrue(
        first.matches("/scripts/global\\.js\\?v=.+"), "expected ?v=<uuid> but was: " + first);

    output.getBuffer().setLength(0);
    AssetUrlTag second = new AssetUrlTag();
    second.setPageContext(pageContext);
    second.setValue("/styles/theme.css");

    assertEquals(TagSupport.SKIP_BODY, second.doStartTag());
    String secondOutput = output.toString();
    assertEquals(
        first.substring(first.indexOf("?v=")),
        secondOutput.substring(secondOutput.indexOf("?v=")),
        "second invocation within the same request reuses the same token");
  }

  @Test
  public void devModeUuidChangesAcrossRequestsWhenLegacyAssetFlagEnabled() throws JspException {
    stubPageContext();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);
    System.setProperty(
        FrontendCacheVersion.LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY, "true");
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());
    String first = output.toString();

    output.getBuffer().setLength(0);
    request.clearAttributes();
    AssetUrlTag next = new AssetUrlTag();
    next.setPageContext(pageContext);
    next.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, next.doStartTag());
    assertNotEquals(first, output.toString());
  }

  @Test
  public void prefixesContextPathForRelativeAssets() throws JspException {
    stubPageContext();
    request.setContextPath("/rspace");
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    servletContext.setAttribute(FrontendCacheVersion.CACHE_VERSION_ATTR, "2.23.0");
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("/rspace/scripts/global.js?v=2.23.0", output.toString());
  }

  @Test
  public void preservesExistingQueryString() throws JspException {
    stubPageContext();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    servletContext.setAttribute(FrontendCacheVersion.CACHE_VERSION_ATTR, "2.23.0");
    tag.setValue("/scripts/foo.js?bar=baz");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("/scripts/foo.js?bar=baz&amp;v=2.23.0", output.toString());
  }

  @Test
  public void omitsQueryWhenNoVersionAvailable() throws JspException {
    stubPageContext();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("/scripts/global.js", output.toString());
  }

  @Test
  public void omitsVersionTokenForLegacyStylesInDevModeByDefault() throws JspException {
    stubPageContext();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);
    tag.setValue("/styles/theme.css");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("/styles/theme.css", output.toString());
  }

  @Test
  public void usesPerRequestUuidForLegacyStylesInDevModeWhenEnabled() throws JspException {
    stubPageContext();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);
    System.setProperty(
        FrontendCacheVersion.LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY, "true");
    tag.setValue("/styles/theme.css");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertTrue(
        output.toString().matches("/styles/theme\\.css\\?v=.+"),
        "expected ?v=<uuid> but was: " + output);
  }

  @Test
  public void blankValueIsRejected() throws JspException {
    tag.setValue("");
    assertThrows(IllegalStateException.class, () -> tag.doStartTag());
  }
}
