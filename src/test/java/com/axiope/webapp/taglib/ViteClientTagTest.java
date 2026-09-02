package com.axiope.webapp.taglib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ViteClientTagTest {

  @Mock private HttpServletRequest request;
  @Mock private PageContext pageContext;
  @Mock private ServletContext servletContext;
  @Mock private JspWriter writer;

  private final StringBuilder output = new StringBuilder();
  private final Map<String, Object> requestAttributes = new LinkedHashMap<>();
  private String originalReactDevModeProperty;

  @BeforeEach
  public void setUp() throws Exception {
    originalReactDevModeProperty = System.getProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY);
    System.clearProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY);
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

  @AfterEach
  public void tearDown() {
    if (originalReactDevModeProperty == null) {
      System.clearProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY);
    } else {
      System.setProperty(
          FrontendCacheVersion.REACT_DEV_MODE_PROPERTY, originalReactDevModeProperty);
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
        output.toString().contains("type=\"module\" src=\"/ui/dist/@vite/client\""),
        "Expected /ui/dist/@vite/client script tag, got: " + output);
    assertTrue(
        output.toString().contains("/ui/dist/@react-refresh"),
        "Expected React refresh preamble, got: " + output);
    assertTrue(
        output.toString().contains("window.__vite_plugin_react_preamble_installed__ = true;"),
        "Expected React refresh marker, got: " + output);
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
        sharedDedupe.add("script:module:/ui/dist/@vite/client"),
        "BundleTag and ViteClientTag must share the dedupe key for @vite/client");
    assertFalse(
        sharedDedupe.add(BundleTag.REACT_PREAMBLE_DEDUPE_KEY),
        "BundleTag and ViteClientTag must share the dedupe key for the React preamble");
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
