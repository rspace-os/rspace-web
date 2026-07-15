package com.researchspace.webapp.filter;

import static com.researchspace.webapp.filter.FullPathSiteMeshFilter.FORWARD_REQUEST_URI;
import static com.researchspace.webapp.filter.FullPathSiteMeshFilter.FORWARD_SERVLET_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class FullPathSiteMeshFilterTest {

  private HttpServletRequest forwardedRequest(String contextPath, String forwardRequestUri) {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getContextPath()).thenReturn(contextPath);
    when(req.getAttribute(FORWARD_REQUEST_URI)).thenReturn(forwardRequestUri);
    // What the container reports for a servlet path-mapped forward: only the mapping prefix.
    lenient().when(req.getAttribute(FORWARD_SERVLET_PATH)).thenReturn("/workspace");
    return req;
  }

  @Test
  void reportsFullForwardedPathAsServletPathSoAjaxExcludeMatches() {
    HttpServletRequest wrapped =
        FullPathSiteMeshFilter.withFullForwardPath(forwardedRequest("", "/workspace/ajax/search"));

    assertEquals("/workspace/ajax/search", wrapped.getAttribute(FORWARD_SERVLET_PATH));
  }

  @Test
  void stripsContextPathFromForwardedUri() {
    HttpServletRequest wrapped =
        FullPathSiteMeshFilter.withFullForwardPath(
            forwardedRequest("/rspace", "/rspace/workspace/ajax/search"));

    assertEquals("/workspace/ajax/search", wrapped.getAttribute(FORWARD_SERVLET_PATH));
  }

  @Test
  void leavesNonForwardedRequestUnchanged() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getAttribute(FORWARD_REQUEST_URI)).thenReturn(null);

    assertSame(req, FullPathSiteMeshFilter.withFullForwardPath(req));
  }

  @Test
  void passesThroughOtherAttributesUnchanged() {
    HttpServletRequest req = forwardedRequest("", "/workspace/ajax/search");
    when(req.getAttribute("com.example.other")).thenReturn("value");

    HttpServletRequest wrapped = FullPathSiteMeshFilter.withFullForwardPath(req);

    assertEquals("value", wrapped.getAttribute("com.example.other"));
  }
}
