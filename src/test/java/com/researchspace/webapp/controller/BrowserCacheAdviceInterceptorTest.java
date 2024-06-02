package com.researchspace.webapp.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

import com.researchspace.testutils.SpringTransactionalTest;
import java.lang.reflect.Method;
import javax.servlet.http.HttpServletRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod;

public class BrowserCacheAdviceInterceptorTest extends SpringTransactionalTest {

  private BrowserCacheAdviceInterceptor interceptor = new BrowserCacheAdviceInterceptor();
  private MockHttpServletResponse response = new MockHttpServletResponse();
  private HttpServletRequest request = new MockHttpServletRequest();

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testHeadersAddedIfAdvisedByAnnotation() throws Exception {

    SysAdminController sysAdminCtrller = new SysAdminController();
    Method method = sysAdminCtrller.getClass().getMethod("getSystemPage", Model.class);
    ServletInvocableHandlerMethod handler =
        new ServletInvocableHandlerMethod(sysAdminCtrller, method);
    interceptor.postHandle(request, response, handler, null);
    assertThat(response.getHeader("Expires"), containsString("1970"));
  }
}
