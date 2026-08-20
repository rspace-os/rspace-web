package com.researchspace.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class RequestUtilsTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock HttpServletRequest mockRequest;

  @Test
  public void testIsAjaxRequest() {
    when(mockRequest.getHeader(RequestUtil.AJAX_REQUEST_HEADER_NAME)).thenReturn("notajax");
    assertFalse(RequestUtil.isAjaxRequest(mockRequest));
    when(mockRequest.getHeader(RequestUtil.AJAX_REQUEST_HEADER_NAME))
        .thenReturn(RequestUtil.AJAX_REQUEST_TYPE);
    assertTrue(RequestUtil.isAjaxRequest(mockRequest));
  }

  @Test
  public void testRemoteAddr() {
    final String defaultFRom = "localhost";
    final String originalHost = "originalHost";
    when(mockRequest.getRemoteAddr()).thenReturn(null);
    assertEquals("unknown", RequestUtil.remoteAddr(mockRequest));
    // default
    when(mockRequest.getRemoteAddr()).thenReturn(defaultFRom);
    when(mockRequest.getRemoteHost()).thenReturn(defaultFRom);

    assertEquals(defaultFRom, RequestUtil.remoteAddr(mockRequest));
    // but use this preferentially (RSPAC-553)
    when(mockRequest.getHeader(RequestUtil.HEADER_X_FORWARDED_FOR)).thenReturn(originalHost);

    assertEquals(originalHost, RequestUtil.remoteAddr(mockRequest));
  }
}
