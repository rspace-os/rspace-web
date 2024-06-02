package com.researchspace.service.impl;

import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.SignupCaptchaVerifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Verification of Google's Re-Captcha token passed as a part of signup request. */
@Service
public class SignupCaptchaVerifierImpl implements SignupCaptchaVerifier {

  public static final Logger log = LogManager.getLogger(SignupCaptchaVerifier.class);

  public static final String MISCONFIGURED_PROPERTIES_LOG_MSG =
      "Captcha signup is enabled, but user.signup.captcha.site.key or "
          + "user.signup.captcha.secret properties are not set. Skipping the verification.";

  @Autowired protected IPropertyHolder properties;

  @Value("${user.signup.captcha.secret}")
  private String googleCaptchaSecret;

  @Override
  public String verifyCaptchaFromRequest(HttpServletRequest request) {

    if (!"true".equals(properties.getSignupCaptchaEnabled())) {
      return CAPTCHA_OK;
    }

    String captchaResponse = request.getParameter("g-recaptcha-response");
    if (StringUtils.isEmpty(captchaResponse)) {
      return ERROR_NO_CAPTCHA_IN_REQUEST;
    }

    String captchaSiteKey = properties.getSignupCaptchaSiteKey();
    if (StringUtils.isEmpty(captchaSiteKey) || StringUtils.isEmpty(googleCaptchaSecret)) {
      log.warn(MISCONFIGURED_PROPERTIES_LOG_MSG);
      return CAPTCHA_OK;
    }

    ResponseEntity<Map<Object, Object>> googleApiResponse =
        verifyRecaptchaAtGoogle(captchaResponse);
    if (googleApiResponse == null || !googleApiResponse.getStatusCode().is2xxSuccessful()) {
      String statusCode =
          googleApiResponse == null ? "null" : googleApiResponse.getStatusCode().toString();
      log.warn(
          "Captcha Verification request not successful: "
              + statusCode
              + ". Skipping the verification.");
      return CAPTCHA_OK;
    }

    Boolean verified = (Boolean) googleApiResponse.getBody().get("success");
    if (!verified) {
      List<String> errorCodes = (List<String>) googleApiResponse.getBody().get("error-codes");
      log.info(
          "Captcha Verification failed, error-codes: " + Arrays.toString(errorCodes.toArray()));
      return ERROR_VERIFICATION_FAILED;
    }

    log.info("Captcha Verification success.");
    return CAPTCHA_OK;
  }

  public ResponseEntity<Map<Object, Object>> verifyRecaptchaAtGoogle(String captchaResponse) {

    String uri = "https://www.google.com/recaptcha/api/siteverify";
    RestTemplate template = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
    map.add("secret", googleCaptchaSecret);
    map.add("response", captchaResponse);

    HttpEntity<MultiValueMap<String, String>> request =
        new HttpEntity<MultiValueMap<String, String>>(map, headers);
    ResponseEntity<Map<Object, Object>> response;
    try {
      response =
          template.postForEntity(uri, request, (Class<Map<Object, Object>>) (Class<?>) Map.class);
    } catch (RestClientException rce) {
      log.warn("RestClientException when trying to verify Google's captcha", rce);
      return null;
    }

    return response;
  }

  /* for testing */
  public void setProperties(IPropertyHolder properties) {
    this.properties = properties;
  }

  public void setGoogleCaptchaSecret(String signupCaptchaSecret) {
    this.googleCaptchaSecret = signupCaptchaSecret;
  }
}
