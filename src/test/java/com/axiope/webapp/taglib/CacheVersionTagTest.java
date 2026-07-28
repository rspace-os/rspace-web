package com.axiope.webapp.taglib;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
public class CacheVersionTagTest {

  @Mock private HttpServletRequest request;
  @Mock private PageContext pageContext;
  @Mock private ServletContext servletContext;
  @Mock private JspWriter writer;

  private final StringBuilder output = new StringBuilder();
  private final Map<String, Object> requestAttributes = new LinkedHashMap<>();
  private final Map<String, Object> servletContextAttributes = new LinkedHashMap<>();
  private CacheVersionTag tag;

  @BeforeEach
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
    assertEquals(true, first.matches("[0-9a-f-]{8,}"), "expected a UUID-like token: " + first);
  }

  @Test
  public void emitsNothingWhenTokenAbsent() throws JspException {
    servletContextAttributes.put(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("", output.toString());
  }
}
