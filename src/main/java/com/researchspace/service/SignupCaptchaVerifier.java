package com.researchspace.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.support.DefaultMessageSourceResolvable;

public interface SignupCaptchaVerifier {

  String ERROR_CAPTCHA_RESPONSE_MISSING = "errors.captcha.response.missing";

  String ERROR_VERIFICATION_FAILED = "errors.captcha.verification.failed";

  /** Verifies captcha provided in request, returning an error or {@code null} if it succeeded. */
  DefaultMessageSourceResolvable verifyCaptchaFromRequest(HttpServletRequest request);
}
