package com.axiope.webapp.taglib;

import static org.junit.Assert.assertEquals;
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

public class CacheVersionTagTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock private HttpServletRequest request;
  @Mock private PageContext pageContext;
  @Mock private ServletContext servletContext;
  @Mock private JspWriter writer;

  private final StringBuilder output = new StringBuilder();
  private final Map<String, Object> requestAttributes = new LinkedHashMap<>();
  private final Map<String, Object> servletContextAttributes = new LinkedHashMap<>();
  private CacheVersionTag tag;

  @Before
  public void setUp() throws Exception {
    tag = new CacheVersionTag();
    tag.setPageContext(pageContext);
    output.setLength(0);
    requestAttributes.clear();
    servletContextAttributes.clear();

    lenient().when(pageContext.getRequest()).thenReturn(request);
    lenient().when(pageContext.getOut()).thenReturn(writer);
    lenient().when(pageContext.getServletContext()).thenReturn(servletContext);
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
  public void emitsProductionVersionToken() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    servletContextAttributes.put(FrontendCacheVersion.CACHE_VERSION_ATTR, "2.23.0");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("2.23.0", output.toString());
  }

  @Test
  public void emitsRequestUuidInDevMode() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    String first = output.toString();
    assertEquals("expected a UUID-like token: " + first, true, first.matches("[0-9a-f-]{8,}"));
  }

  @Test
  public void emitsNothingWhenTokenAbsent() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("", output.toString());
  }
}
