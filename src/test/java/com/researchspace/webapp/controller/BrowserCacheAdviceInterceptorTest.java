package com.researchspace.webapp.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import com.researchspace.testutils.SpringTransactionalTest;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod;

public class BrowserCacheAdviceInterceptorTest extends SpringTransactionalTest {

  private BrowserCacheAdviceInterceptor interceptor = new BrowserCacheAdviceInterceptor();
  private MockHttpServletResponse response = new MockHttpServletResponse();
  private HttpServletRequest request = new MockHttpServletRequest();

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
