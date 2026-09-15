package com.axiope.webapp.taglib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockJspWriter;
import org.springframework.mock.web.MockServletContext;

@ExtendWith(MockitoExtension.class)
public class CacheVersionTagTest {

  @Mock private PageContext pageContext;

  private final StringWriter output = new StringWriter();
  private MockHttpServletRequest request;
  private MockServletContext servletContext;
  private JspWriter writer;
  private CacheVersionTag tag;

  @BeforeEach
  public void setUp() {
    tag = new CacheVersionTag();
    output.getBuffer().setLength(0);
    servletContext = new MockServletContext();
    request = new MockHttpServletRequest(servletContext);
    writer = new MockJspWriter(output);
    tag.setPageContext(pageContext);
  }

  private void stubPageContext() {
    when(pageContext.getRequest()).thenReturn(request);
    when(pageContext.getServletContext()).thenReturn(servletContext);
  }

  private void stubWriter() {
    when(pageContext.getOut()).thenReturn(writer);
  }

  @Test
  public void emitsProductionVersionToken() throws JspException {
    stubPageContext();
    stubWriter();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);
    servletContext.setAttribute(FrontendCacheVersion.CACHE_VERSION_ATTR, "2.23.0");

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("2.23.0", output.toString());
  }

  @Test
  public void emitsRequestUuidInDevMode() throws JspException {
    stubPageContext();
    stubWriter();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.TRUE);

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    String first = output.toString();
    assertEquals(true, first.matches("[0-9a-f-]{8,}"), "expected a UUID-like token: " + first);
  }

  @Test
  public void emitsNothingWhenTokenAbsent() throws JspException {
    stubPageContext();
    servletContext.setAttribute(FrontendCacheVersion.DEV_MODE_CACHE_ATTR, Boolean.FALSE);

    assertEquals(TagSupport.SKIP_BODY, tag.doStartTag());

    assertEquals("", output.toString());
  }
}
