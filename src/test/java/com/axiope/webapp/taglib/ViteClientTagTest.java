package com.axiope.webapp.taglib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.TagSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ViteClientTagTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock private HttpServletRequest request;
  @Mock private PageContext pageContext;
  @Mock private ServletContext servletContext;
  @Mock private JspWriter writer;

  private final StringBuilder output = new StringBuilder();
  private final Map<String, Object> requestAttributes = new LinkedHashMap<>();
  private String originalReactDevModeProperty;

  @Before
  public void setUp() throws Exception {
    originalReactDevModeProperty = System.getProperty(BundleTag.REACT_DEV_MODE_PROPERTY);
    System.clearProperty(BundleTag.REACT_DEV_MODE_PROPERTY);
    output.setLength(0);
    requestAttributes.clear();

    lenient().when(pageContext.getRequest()).thenReturn(request);
    lenient().when(pageContext.getOut()).thenReturn(writer);
    lenient().when(pageContext.getServletContext()).thenReturn(servletContext);
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

  @After
  public void tearDown() {
    if (originalReactDevModeProperty == null) {
      System.clearProperty(BundleTag.REACT_DEV_MODE_PROPERTY);
    } else {
      System.setProperty(BundleTag.REACT_DEV_MODE_PROPERTY, originalReactDevModeProperty);
    }
  }

  @Test
  public void emitsViteClientInHmrMode() throws JspException {
    ViteClientTag tag =
        new ViteClientTag() {
          @Override
          boolean isHmrEnabled() {
            return true;
          }
        };
    tag.setPageContext(pageContext);

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());
    assertTrue(
        "Expected /ui/dist/@vite/client script tag, got: " + output,
        output.toString().contains("type=\"module\" src=\"/ui/dist/@vite/client\""));
    assertTrue(
        "Expected React refresh preamble, got: " + output,
        output.toString().contains("/ui/dist/@react-refresh"));
    assertTrue(
        "Expected React refresh marker, got: " + output,
        output.toString().contains("window.__vite_plugin_react_preamble_installed__ = true;"));
  }

  @Test
  public void emitsNothingInProductionMode() throws JspException {
    ViteClientTag tag =
        new ViteClientTag() {
          @Override
          boolean isHmrEnabled() {
            return false;
          }
        };
    tag.setPageContext(pageContext);

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());
    assertEquals("", output.toString());
  }

  @Test
  public void deduplicatesAgainstSubsequentBundleTagInSameRequest() throws JspException {
    Set<String> sharedDedupe = new LinkedHashSet<>();
    requestAttributes.put(BundleTag.RENDERED_ASSETS_ATTR, sharedDedupe);

    ViteClientTag clientTag =
        new ViteClientTag() {
          @Override
          boolean isHmrEnabled() {
            return true;
          }
        };
    clientTag.setPageContext(pageContext);
    clientTag.doStartTag();

    // Simulate a BundleTag.renderHmrBundle() emitting @vite/client at the same URL — it should be
    // suppressed by the shared dedupe set populated by ViteClientTag above.
    assertFalse(
        "BundleTag and ViteClientTag must share the dedupe key for @vite/client",
        sharedDedupe.add("script:module:/ui/dist/@vite/client"));
    assertFalse(
        "BundleTag and ViteClientTag must share the dedupe key for the React preamble",
        sharedDedupe.add(BundleTag.REACT_PREAMBLE_DEDUPE_KEY));
  }

  @Test
  public void doesNotEmitTwiceWhenRenderedRepeatedly() throws JspException {
    ViteClientTag tag =
        new ViteClientTag() {
          @Override
          boolean isHmrEnabled() {
            return true;
          }
        };
    tag.setPageContext(pageContext);

    tag.doStartTag();
    int lengthAfterFirst = output.length();
    tag.doStartTag();
    assertEquals(lengthAfterFirst, output.length());
  }
}
