package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class APIAvailableInterceptorTest {
  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock ApiAvailabilityHandler availableHandler;
  APIAvailableInterceptor apiAvailability;
  MockHttpServletRequest req;
  MockHttpServletResponse resp;

  @Before
  public void setUp() throws Exception {
    apiAvailability = new APIAvailableInterceptor();
    apiAvailability.setApiHandler(availableHandler);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testPreHandleHappyCase() throws IOException {
    Mockito.when(availableHandler.isAvailable(null, req))
        .thenReturn(new ServiceOperationResult<String>("", true));
    assertTrue(apiAvailability.preHandle(req, resp, null));
  }

  @Test
  public void testPreHandleAPIUnavailableCase() throws Exception {
    Mockito.when(availableHandler.isAvailable(null, req))
        .thenReturn(new ServiceOperationResult<String>("error", false));
    assertExceptionThrown(
        () -> apiAvailability.preHandle(req, resp, null), APIUnavailableException.class);
  }
}
