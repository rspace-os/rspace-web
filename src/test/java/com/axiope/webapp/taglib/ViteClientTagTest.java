package com.axiope.webapp.taglib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.io.StringWriter;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockJspWriter;

@ExtendWith(MockitoExtension.class)
public class ViteClientTagTest {

  @Mock private PageContext pageContext;

  private final StringWriter output = new StringWriter();
  private MockHttpServletRequest request;
  private JspWriter writer;
  private String originalReactDevModeProperty;

  @BeforeEach
  public void setUp() {
    originalReactDevModeProperty = System.getProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY);
    System.clearProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY);
    output.getBuffer().setLength(0);
    request = new MockHttpServletRequest();
    writer = new MockJspWriter(output);
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

  private void stubPageContext() {
    when(pageContext.getRequest()).thenReturn(request);
    when(pageContext.getOut()).thenReturn(writer);
  }

  @Test
  public void emitsViteClientInHmrMode() throws JspException {
    stubPageContext();
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
    stubPageContext();
    Set<String> sharedDedupe = new LinkedHashSet<>();
    request.setAttribute(BundleTag.RENDERED_ASSETS_ATTR, sharedDedupe);

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
    stubPageContext();
    ViteClientTag tag =
        new ViteClientTag() {
          @Override
          boolean isHmrEnabled() {
            return true;
          }
        };
    tag.setPageContext(pageContext);

    tag.doStartTag();
    int lengthAfterFirst = output.getBuffer().length();
    tag.doStartTag();
    assertEquals(lengthAfterFirst, output.getBuffer().length());
  }
}
