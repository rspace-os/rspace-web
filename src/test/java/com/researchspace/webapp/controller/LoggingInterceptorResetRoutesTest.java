package com.researchspace.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod;

class LoggingInterceptorResetRoutesTest {

  private static final String TOKEN = "tok-SECRET-1";
  private static final String PASSWORD = "Pw-SECRET-2";

  static Stream<Arguments> resetRoutes() throws NoSuchMethodException {
    Object signup = new SignupController();
    Object verification = new VerificationPasswordController();
    return Stream.of(
        Arguments.of(
            "/signup/passwordResetReply",
            signup,
            SignupController.class.getMethod("getPasswordResetPage", String.class)),
        Arguments.of(
            "/signup/passwordResetReply",
            signup,
            SignupController.class.getMethod(
                "submitPasswordResetPage",
                PasswordResetCommand.class,
                BindingResult.class,
                HttpServletRequest.class)),
        Arguments.of(
            "/vfpwd/verificationPasswordResetReply",
            verification,
            VerificationPasswordController.class.getMethod("getPasswordResetPage", String.class)),
        Arguments.of(
            "/vfpwd/verificationPasswordResetReply",
            verification,
            VerificationPasswordController.class.getMethod(
                "submitPasswordResetPage",
                PasswordResetCommand.class,
                BindingResult.class,
                HttpServletRequest.class)));
  }

  @ParameterizedTest
  @MethodSource("resetRoutes")
  void resetRouteDoesNotLogTokenOrPassword(String uri, Object controller, Method method)
      throws Exception {
    StringAppenderForTestLogging logged =
        CoreTestUtils.configureStringLogger(LoggingInterceptor.getLog());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(uri);
    request.setRemoteAddr("23.16.23.25");
    request.setParameter("token", TOKEN);
    request.setParameter("password", PASSWORD);
    request.setParameter("confirmPassword", PASSWORD);

    assertTrue(
        new LoggingInterceptor()
            .preHandle(
                request,
                new MockHttpServletResponse(),
                new ServletInvocableHandlerMethod(controller, method)));

    assertThat(logged.logContents)
        .contains(uri)
        .doesNotContain(TOKEN)
        .doesNotContain(PASSWORD)
        .doesNotContain("token=")
        .doesNotContain("password=");
  }
}
