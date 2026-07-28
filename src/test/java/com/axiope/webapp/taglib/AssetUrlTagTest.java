package com.axiope.webapp.taglib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AssetUrlTagTest {

  @Mock private HttpServletRequest request;
  @Mock private PageContext pageContext;
  @Mock private ServletContext servletContext;
  @Mock private JspWriter writer;

  private final StringBuilder output = new StringBuilder();
  private final Map<String, Object> requestAttributes = new LinkedHashMap<>();
  private final Map<String, Object> servletContextAttributes = new LinkedHashMap<>();
  private AssetUrlTag tag;

  @BeforeEach
  public void setUp() throws Exception {
    tag = new AssetUrlTag();
    tag.setPageContext(pageContext);
    output.setLength(0);
    requestAttributes.clear();
    servletContextAttributes.clear();
    System.clearProperty(FrontendCacheVersion.LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY);

    lenient().when(pageContext.getRequest()).thenReturn(request);
    lenient().when(pageContext.getOut()).thenReturn(writer);
    lenient().when(pageContext.getServletContext()).thenReturn(servletContext);
    lenient().when(request.getContextPath()).thenReturn("");
    lenient()
        .when(servletContext.getAttribute(anyString()))
        .thenAnswer(
            invocation -> servletContextAttributes.get(invocation.getArgument(0, String.class)));
    lenient()
        .doAnswer(
            invocation -> {
              servletContextAttributes.put(
                  invocation.getArgument(0, String.class), invocation.getArgument(1));
              return null;
            })
        .when(servletContext)
        .setAttribute(anyString(), org.mockito.ArgumentMatchers.any());
    lenient()
        .when(request.getAttribute(anyString()))
        .thenAnswer(invocation -> requestAttributes.get(invocation.getArgument(0, String.class)));
    lenient()
        .doAnswer(
            invocation -> {
              requestAttributes.put(
                  invocation.getArgument(0, String.class), invocation.getArgument(1));
              return null;
            })
        .when(request)
        .setAttribute(anyString(), org.mockito.ArgumentMatchers.any());

    lenient()
        .doAnswer(
            invocation -> {
              output.append(invocation.getArgument(0, String.class));
              return null;
            })
        .when(writer)
        .write(anyString());
  }

  @Test
  public void appendsVersionTokenInProductionMode() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    servletContextAttributes.put(FrontendCacheVersion.CACHE_VERSION_ATTR, "2.23.0");
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("/scripts/global.js?v=2.23.0", output.toString());
  }

  @Test
  public void cachesResolvedUrlsInProductionMode() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    servletContextAttributes.put(FrontendCacheVersion.CACHE_VERSION_ATTR, "2.23.0");
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    AssetUrlTag.ProductionUrlCache cache =
        (AssetUrlTag.ProductionUrlCache)
            servletContextAttributes.get(AssetUrlTag.PRODUCTION_URL_CACHE_ATTR);
    assertEquals(1, cache.urls.size());
    assertTrue(cache.urls.containsValue("/scripts/global.js?v=2.23.0"));

    output.setLength(0);
    AssetUrlTag second = new AssetUrlTag();
    second.setPageContext(pageContext);
    second.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, second.doStartTag());
    assertEquals("/scripts/global.js?v=2.23.0", output.toString());
    assertSame(
        cache,
        servletContextAttributes.get(AssetUrlTag.PRODUCTION_URL_CACHE_ATTR),
        "the cache must be reused, not rebuilt per invocation");
    assertEquals(1, cache.urls.size());
  }

  @Test
  public void doesNotPopulateProductionCacheInDevMode() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);
    System.setProperty(
        FrontendCacheVersion.LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY, "true");
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertFalse(servletContextAttributes.containsKey(AssetUrlTag.PRODUCTION_URL_CACHE_ATTR));
  }

  @Test
  public void omitsVersionTokenForLegacyScriptsInDevModeByDefault() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("/scripts/global.js", output.toString());
  }

  @Test
  public void usesPerRequestUuidForLegacyScriptsInDevModeWhenEnabled() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);
    System.setProperty(
        FrontendCacheVersion.LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY, "true");
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    String first = output.toString();
    assertTrue(
        first.matches("/scripts/global\\.js\\?v=.+"), "expected ?v=<uuid> but was: " + first);

    output.setLength(0);
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
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);
    System.setProperty(
        FrontendCacheVersion.LEGACY_ASSET_CACHE_BUSTING_IN_DEV_MODE_PROPERTY, "true");
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());
    String first = output.toString();

    output.setLength(0);
    requestAttributes.clear();
    AssetUrlTag next = new AssetUrlTag();
    next.setPageContext(pageContext);
    next.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, next.doStartTag());
    assertNotEquals(first, output.toString());
  }

  @Test
  public void prefixesContextPathForRelativeAssets() throws JspException {
    lenient().when(request.getContextPath()).thenReturn("/rspace");
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    servletContextAttributes.put(FrontendCacheVersion.CACHE_VERSION_ATTR, "2.23.0");
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("/rspace/scripts/global.js?v=2.23.0", output.toString());
  }

  @Test
  public void preservesExistingQueryString() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    servletContextAttributes.put(FrontendCacheVersion.CACHE_VERSION_ATTR, "2.23.0");
    tag.setValue("/scripts/foo.js?bar=baz");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("/scripts/foo.js?bar=baz&amp;v=2.23.0", output.toString());
  }

  @Test
  public void omitsQueryWhenNoVersionAvailable() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    tag.setValue("/scripts/global.js");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("/scripts/global.js", output.toString());
  }

  @Test
  public void omitsVersionTokenForLegacyStylesInDevModeByDefault() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);
    tag.setValue("/styles/theme.css");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("/styles/theme.css", output.toString());
  }

  @Test
  public void usesPerRequestUuidForLegacyStylesInDevModeWhenEnabled() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);
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
    assertThrows(
        IllegalStateException.class,
        () -> {
          tag.setValue("");
          tag.doStartTag();
        });
  }
}
