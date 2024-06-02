package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.model.dtos.RunAsUserCommand;
import com.researchspace.testutils.SpringTransactionalTest;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod;

public class LoggingInterceptorTest extends SpringTransactionalTest {

  @Autowired private LoggingInterceptor logInterceptor;

  private MockHttpServletRequest httpRequest;
  private MockHttpServletResponse httpResponse;
  private StringAppenderForTestLogging strglogger;

  @Before
  public void setUp() {
    strglogger = configureTestLogger(LoggingInterceptor.getLog());
    httpRequest = new MockHttpServletRequest();
    httpResponse = new MockHttpServletResponse();
  }

  @Test
  public void testPreHandleOfMethodToBeLogged() throws Exception {
    // set up controller to intercept
    DashboardController dc = new DashboardController();
    Method method =
        dc.getClass().getMethod("markAsRead", Principal.class, Collection.class, Boolean.class);
    ServletInvocableHandlerMethod handler = new ServletInvocableHandlerMethod(dc, method);
    // setup dummy http request
    final String remoteAddr = "23.16.23.256";
    httpRequest.setRemoteAddr(remoteAddr);
    final String requestURI = "/dashboard/ajax/markAsRead";
    httpRequest.setRequestURI(requestURI);
    httpRequest.setParameter("name", "valueToBeLogged");
    // should always return true
    assertTrue(logInterceptor.preHandle(httpRequest, httpResponse, handler));
    // test log contents
    assertTrue(strglogger.logContents.contains(requestURI));
    assertTrue(strglogger.logContents.contains(remoteAddr));
    // check request params are logged
    assertTrue(strglogger.logContents.contains("valueToBeLogged"));
  }

  @Test
  public void testPreHandleOfIgnoredMethod() throws Exception {
    // set up controller to intercept
    DashboardController dc = new DashboardController();
    Method method = dc.getClass().getMethod("poll");
    ServletInvocableHandlerMethod handler = new ServletInvocableHandlerMethod(dc, method);
    setUpRequestCoreData();
    assertTrue(logInterceptor.preHandle(httpRequest, httpResponse, handler));
    assertTrue(strglogger.logContents.isEmpty());
  }

  @Test
  public void testPreHandleOfSecureMethodDoesNotLogParams() throws Exception {
    // set up controller to intercept
    SysAdminController dc = new SysAdminController();
    Method method =
        dc.getClass()
            .getMethod(
                "runAs",
                Model.class,
                HttpSession.class,
                RunAsUserCommand.class,
                BindingResult.class);
    ServletInvocableHandlerMethod handler = new ServletInvocableHandlerMethod(dc, method);
    final String remoteAddr = "http://anywhere.com";
    httpRequest.setRemoteAddr(remoteAddr);
    final String requestURI = "/ajax/runAs";
    httpRequest.setRequestURI(requestURI);
    httpRequest.setParameter("sysadminPassword", "should be ignored");
    httpRequest.setParameter("runAsUsername", "should be logged");
    assertTrue(logInterceptor.preHandle(httpRequest, httpResponse, handler));

    assertTrue(strglogger.logContents.contains(requestURI));
    assertFalse(strglogger.logContents.contains("should be ignored"));
    assertTrue(strglogger.logContents.contains("should be logged"));
  }

  protected void setUpRequestCoreData() {
    final String remoteAddr = "http://anywhere.com";
    httpRequest.setRemoteAddr(remoteAddr);
    final String requestURI = "/ajax/poll";
    httpRequest.setRequestURI(requestURI);
  }

  @Test
  public void testPreLoggingOfLArgeDataTruncatesLog() throws Exception {
    DashboardController dc = new DashboardController();
    Method method = dc.getClass().getMethod("poll");
    ServletInvocableHandlerMethod handler = new ServletInvocableHandlerMethod(dc, method);
    setUpRequestCoreData();
    String bigData = CoreTestUtils.getRandomName(500);
    httpRequest.setParameter("bigString", bigData);
    logInterceptor.preHandle(httpRequest, httpResponse, handler);
    // assert request param is unaffected
    assertTrue(httpRequest.getParameter("bigString").equals(bigData));

    // assertlog is truncated
    assertTrue(strglogger.logContents.length() < 150); // 100 + boilerplate log statement
  }

  @Test
  public void testURLParsing() {
    Pattern p = logInterceptor.getUrlNamePattern();
    Matcher m = p.matcher("/app/x/y/1234");
    m.find();
    assertEquals("/x/y/", logInterceptor.extractTagFromURL("/app/x/y/1234"));
  }
}
