package com.researchspace.service;

import javax.servlet.http.HttpServletRequest;

public interface SignupCaptchaVerifier {

  String CAPTCHA_OK = "OK";

  String ERROR_NO_CAPTCHA_IN_REQUEST = "errors.no.captcha.in.request";

  String ERROR_VERIFICATION_FAILED = "errors.captcha.verification.failed";

  /** verifies captcha provided in request, returns error code or null if succeeded. */
  String verifyCaptchaFromRequest(HttpServletRequest request);
}
