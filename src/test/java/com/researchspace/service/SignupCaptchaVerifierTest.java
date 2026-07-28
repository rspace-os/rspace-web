package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.impl.SignupCaptchaVerifierImpl;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
public class SignupCaptchaVerifierTest extends SpringTransactionalTest {

  private SignupCaptchaVerifierImpl captchaVerifier = new SignupCaptchaVerifierImpl();
  @Mock private IPropertyHolder propHolder;

  @Value("${user.signup.captcha.site.key}")
  private String captchaSiteKey;

  @Value("${user.signup.captcha.secret}")
  private String captchaSecret;

  private MockHttpServletRequest mockRequest;

  private StringAppenderForTestLogging stringLogger;

  @BeforeEach
  public void setUp() throws Exception {

    captchaVerifier.setProperties(propHolder);
    captchaVerifier.setGoogleCaptchaVerifyUrl("https://www.google.com/recaptcha/api/siteverify");

    stringLogger = CoreTestUtils.configureStringLogger(SignupCaptchaVerifierImpl.log);
    mockRequest = new MockHttpServletRequest();
  }

  @Test
  public void testConfigurationErrorsForCaptchaTokenVerification() throws Exception {

    /* captcha disabled */
    when(propHolder.getSignupCaptchaEnabled()).thenReturn("false");
    assertEquals(
        SignupCaptchaVerifier.CAPTCHA_OK, captchaVerifier.verifyCaptchaFromRequest(mockRequest));
    assertEquals("", stringLogger.logContents);

    /* captcha enabled, but token not provided in request */
    when(propHolder.getSignupCaptchaEnabled()).thenReturn("true");
    assertEquals(
        SignupCaptchaVerifier.ERROR_CAPTCHA_RESPONSE_MISSING,
        captchaVerifier.verifyCaptchaFromRequest(mockRequest));

    /* Captcha enabled and provided, but google site key or secret key not in deployment properties.
     * This should not stop the signup, but write a message in log. */
    mockRequest.addParameter("g-recaptcha-response", new String[] {"dummy"});
    assertEquals(
        SignupCaptchaVerifier.CAPTCHA_OK, captchaVerifier.verifyCaptchaFromRequest(mockRequest));
    assertEquals(
        SignupCaptchaVerifierImpl.MISCONFIGURED_PROPERTIES_LOG_MSG, stringLogger.logContents);
    stringLogger.logContents = ""; // clear contents
  }

  @Test
  @EnabledIfSystemProperty(named = "nightly", matches = ".*")
  public void testCaptchaVerificationErrorFromGoogleApi() throws Exception {

    /* deployment properties incorrect */
    when(propHolder.getSignupCaptchaEnabled()).thenReturn("true");
    mockRequest.addParameter("g-recaptcha-response", new String[] {"dummy"});
    when(propHolder.getSignupCaptchaSiteKey()).thenReturn("dummy_key");
    captchaVerifier.setGoogleCaptchaSecret("dummy_secret");
    assertEquals(
        SignupCaptchaVerifier.ERROR_VERIFICATION_FAILED,
        captchaVerifier.verifyCaptchaFromRequest(mockRequest));
    assertEquals(
        "Captcha Verification failed, error-codes: [invalid-input-response]",
        stringLogger.logContents);
    stringLogger.logContents = ""; // clear contents

    /* 'localhost' deployment properties */
    when(propHolder.getSignupCaptchaSiteKey()).thenReturn(captchaSiteKey);
    captchaVerifier.setGoogleCaptchaSecret(captchaSecret);
    assertEquals(
        SignupCaptchaVerifier.ERROR_VERIFICATION_FAILED,
        captchaVerifier.verifyCaptchaFromRequest(mockRequest));
    assertEquals(
        "Captcha Verification failed, error-codes: [invalid-input-response]",
        stringLogger.logContents);
  }
}
