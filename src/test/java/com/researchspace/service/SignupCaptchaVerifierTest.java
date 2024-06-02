package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.service.impl.SignupCaptchaVerifierImpl;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpServletRequest;

@RunWith(ConditionalTestRunner.class)
public class SignupCaptchaVerifierTest extends SpringTransactionalTest {

  private SignupCaptchaVerifierImpl captchaVerifier = new SignupCaptchaVerifierImpl();

  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock private IPropertyHolder propHolder;

  @Value("${user.signup.captcha.site.key}")
  private String captchaSiteKey;

  @Value("${user.signup.captcha.secret}")
  private String captchaSecret;

  private MockHttpServletRequest mockRequest;

  private StringAppenderForTestLogging stringLogger;

  @Before
  public void setUp() throws Exception {

    captchaVerifier.setProperties(propHolder);

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
        SignupCaptchaVerifier.ERROR_NO_CAPTCHA_IN_REQUEST,
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
  @RunIfSystemPropertyDefined("nightly")
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
        "Captcha Verification failed, error-codes: [invalid-input-secret]",
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
