package com.axiope.webapp.taglib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.TagSupport;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AssetUrlTagTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock private HttpServletRequest request;
  @Mock private PageContext pageContext;
  @Mock private ServletContext servletContext;
  @Mock private JspWriter writer;

  private final StringBuilder output = new StringBuilder();
  private final Map<String, Object> requestAttributes = new LinkedHashMap<>();
  private final Map<String, Object> servletContextAttributes = new LinkedHashMap<>();
  private AssetUrlTag tag;

  @Before
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
        "expected ?v=<uuid> but was: " + first, first.matches("/scripts/global\\.js\\?v=.+"));

    output.setLength(0);
    AssetUrlTag second = new AssetUrlTag();
    second.setPageContext(pageContext);
    second.setValue("/styles/theme.css");

    assertEquals(TagSupport.SKIP_BODY, second.doStartTag());
    String secondOutput = output.toString();
    assertEquals(
        "second invocation within the same request reuses the same token",
        first.substring(first.indexOf("?v=")),
        secondOutput.substring(secondOutput.indexOf("?v=")));
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
        "expected ?v=<uuid> but was: " + output,
        output.toString().matches("/styles/theme\\.css\\?v=.+"));
  }

  @Test(expected = IllegalStateException.class)
  public void blankValueIsRejected() throws JspException {
    tag.setValue("");
    tag.doStartTag();
  }
}
