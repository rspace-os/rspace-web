package com.researchspace.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.model.dtos.RunAsUserCommand;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.github.GitHubController;
import com.researchspace.webapp.integrations.slack.SlackController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  @BeforeEach
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
    assertThat(strglogger.logContents).contains(requestURI);
    assertThat(strglogger.logContents).contains(remoteAddr);
    // check request params are logged
    assertThat(strglogger.logContents).contains("valueToBeLogged");
  }

  @Test
  public void testPreHandleOfIgnoredMethod() throws Exception {
    // set up controller to intercept
    DashboardController dc = new DashboardController();
    Method method = dc.getClass().getMethod("poll");
    ServletInvocableHandlerMethod handler = new ServletInvocableHandlerMethod(dc, method);
    setUpRequestCoreData();
    assertTrue(logInterceptor.preHandle(httpRequest, httpResponse, handler));
    assertThat(strglogger.logContents).isEmpty();
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

    assertThat(strglogger.logContents).contains(requestURI);
    assertThat(strglogger.logContents).doesNotContain("should be ignored");
    assertThat(strglogger.logContents).contains("should be logged");
  }

  @Test
  public void testOAuthCallbacksDoNotLogCodeOrState() throws Exception {
    assertOAuthCallbackParamsAreNotLogged(
        new GitHubController(),
        GitHubController.class.getMethod(
            "onAuthorization", Map.class, Model.class, Principal.class, HttpServletRequest.class),
        "/github/redirect_uri",
        "github-code-secret",
        "github-state-secret");
    assertOAuthCallbackParamsAreNotLogged(
        new SlackController(),
        SlackController.class.getMethod(
            "handleSlackRedirect",
            Map.class,
            Model.class,
            Principal.class,
            HttpServletRequest.class),
        "/slack/redirect_uri",
        "slack-code-secret",
        "slack-state-secret");

    assertThat(strglogger.logContents)
        .contains("/github/redirect_uri", "/slack/redirect_uri")
        .doesNotContain(
            "github-code-secret", "github-state-secret", "slack-code-secret", "slack-state-secret");
  }

  private void assertOAuthCallbackParamsAreNotLogged(
      Object controller, Method method, String uri, String code, String state) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(uri);
    request.setParameter("code", code);
    request.setParameter("state", state);

    ServletInvocableHandlerMethod handler = new ServletInvocableHandlerMethod(controller, method);
    assertTrue(logInterceptor.preHandle(request, httpResponse, handler));
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
    assertThat(strglogger.logContents.length()).isLessThan(150); // 100 + boilerplate log statement
  }

  @Test
  public void testURLParsing() {
    Pattern p = logInterceptor.getUrlNamePattern();
    Matcher m = p.matcher("/app/x/y/1234");
    m.find();
    assertEquals("/x/y/", logInterceptor.extractTagFromURL("/app/x/y/1234"));
  }
}
