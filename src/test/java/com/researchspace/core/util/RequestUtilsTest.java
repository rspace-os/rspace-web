package com.researchspace.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RequestUtilsTest {
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

    assertEquals(defaultFRom, RequestUtil.remoteAddr(mockRequest));
    // but use this preferentially (RSPAC-553)
    when(mockRequest.getHeader(RequestUtil.HEADER_X_FORWARDED_FOR)).thenReturn(originalHost);

    assertEquals(originalHost, RequestUtil.remoteAddr(mockRequest));
  }
}
