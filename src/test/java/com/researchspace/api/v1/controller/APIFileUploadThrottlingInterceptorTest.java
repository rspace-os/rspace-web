package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.APIFileUploadThrottlingInterceptor.BYTES_PER_MB;
import static com.researchspace.api.v1.controller.APIFileUploadThrottlingInterceptor.X_UPLOAD_LIMIT_LIMIT;
import static com.researchspace.api.v1.controller.APIFileUploadThrottlingInterceptor.X_UPLOAD_LIMIT_REMAINING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.throttling.APIFileUploadStats;
import com.researchspace.api.v1.throttling.APIFileUploadThrottler;
import com.researchspace.core.util.throttling.ThrottleInterval;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

public class APIFileUploadThrottlingInterceptorTest {
  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock APIFileUploadThrottler throttler;
  MockMultipartHttpServletRequest request;
  MockHttpServletResponse response;

  @InjectMocks APIFileUploadThrottlingInterceptor interceptor;

  @Before
  public void setUp() throws Exception {
    request = new MockMultipartHttpServletRequest();
    response = new MockHttpServletResponse();
    request.setMethod("POST");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testPreHandleOK() throws IOException {
    MockMultipartFile mockFile = setUpRequestWithFileUpload();
    when(throttler.proceed("any", getFileSizeInMb(mockFile))).thenReturn(true);
    setUpStats();
    assertTrue(interceptor.preHandle(request, response, null));
    assertFileUploadResponseSet();
    // verify was called
    verify(throttler).proceed("any", getFileSizeInMb(mockFile));
  }

  @Test
  public void testPreHandleReturnsFalseIfThrottlerFalse() throws IOException {
    MockMultipartFile mockFile = setUpRequestWithFileUpload();
    when(throttler.proceed("any", getFileSizeInMb(mockFile))).thenReturn(false);
    setUpStats();
    assertFalse(interceptor.preHandle(request, response, null));
    assertFileUploadResponseSet();
    // verify was called
    verify(throttler).proceed("any", getFileSizeInMb(mockFile));
  }

  private void assertFileUploadResponseSet() {
    assertEquals("100.0", response.getHeader(X_UPLOAD_LIMIT_LIMIT));
    assertEquals("50.0", response.getHeader(X_UPLOAD_LIMIT_REMAINING));
  }

  @Test
  public void testPreHandleThrottlerNotCalledIfNotPOST() throws IOException {
    MockMultipartFile mockFile = setUpRequestWithFileUpload();
    request.setMethod("GET");
    setUpStats();
    assertTrue(interceptor.preHandle(request, response, null));
    assertFileUploadResponseHeadersNotSet();
    // verify was not called
    verify(throttler, never()).proceed("any", getFileSizeInMb(mockFile));
  }

  private double getFileSizeInMb(MockMultipartFile mockFile) {
    return (mockFile).getSize() / (double) BYTES_PER_MB;
  }

  private void assertFileUploadResponseHeadersNotSet() {
    assertNull(response.getHeader(X_UPLOAD_LIMIT_LIMIT));
    assertNull(response.getHeader(X_UPLOAD_LIMIT_REMAINING));
  }

  private void setUpStats() {
    Optional<APIFileUploadStats> stats = getStats(100, 50);
    Mockito.when(throttler.getStats("any", ThrottleInterval.HOUR)).thenReturn(stats);
  }

  private MockMultipartFile setUpRequestWithFileUpload() throws IOException {
    InputStream is = RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("ODP.odp");
    MockMultipartFile mockFile = new MockMultipartFile("file", is);
    request.addFile(mockFile);
    request.addHeader("apiKey", "any");
    return mockFile;
  }

  private Optional<APIFileUploadStats> getStats(int uploaded, int remaining) {
    APIFileUploadStats stats = new APIFileUploadStats(uploaded, remaining);
    return Optional.of(stats);
  }
}
