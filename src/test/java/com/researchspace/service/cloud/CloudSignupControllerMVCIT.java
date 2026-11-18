package com.researchspace.service.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

import com.researchspace.googleauth.ExternalAuthTokenVerifier;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.impl.license.NoCheckLicenseService;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.webapp.controller.ExternalAuthController;
import com.researchspace.webapp.controller.MVCTestBase;
import com.researchspace.webapp.controller.SignupController;
import java.security.Principal;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
@CommunityTestContext
public class CloudSignupControllerMVCIT extends MVCTestBase {

  private static final String VALID_PWD = "pwd12345";

  class PrincipalSetter implements RequestPostProcessor {
    Principal principal;

    public PrincipalSetter(Principal principal) {
      super();
      this.principal = principal;
    }

    @Override
    public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
      request.setUserPrincipal(principal);
      return request;
    }
  }

  @Autowired SignupController signupController;
  @Autowired IPropertyHolder propertyHolder;

  @Autowired private WebApplicationContext wac;

  @Autowired SessionFactory sf;

  @Autowired private ExternalAuthController externalAuthController;
  private ExternalAuthTokenVerifier externalAuthVerifier;
  private Object originalVerifier;
  private Object externalAuthTarget;

  private MockMvc mockMvc;

  @BeforeEach
  public void setup() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    super.setUp();
    externalAuthTarget = AopTestUtils.getUltimateTargetObject(externalAuthController);
    originalVerifier = ReflectionTestUtils.getField(externalAuthTarget, "externalAuthVerifier");
    externalAuthVerifier = mock(ExternalAuthTokenVerifier.class);
    ReflectionTestUtils.setField(externalAuthTarget, "externalAuthVerifier", externalAuthVerifier);
  }

  @AfterEach
  public void tearDown() throws Exception {
    ReflectionTestUtils.setField(externalAuthTarget, "externalAuthVerifier", originalVerifier);
    super.tearDown();
    signupController.setLicenseService(new NoCheckLicenseService());
  }

  @Test
  public void postSignupCloudFormTest() throws Exception {
    assertTrue(propertyHolder.isCloud());
    final String uname = getRandomAlphabeticString("newUser");
    final String EXPECTED_AFFILIATION = "Uni of RSpace";

    // needs affiliation
    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("termsAccepted", "true")
                .param("username", uname)
                .param("email", "xxx")
                .param("password", VALID_PWD)
                .param("confirmPassword", VALID_PWD)
                .param("firstName", "pwd")
                .param("lastName", "pwd")
                .param("affiliation", ""))
        .andExpect(model().attributeHasFieldErrors("user", "affiliation"));
    // affiliation  now included
    this.mockMvc
        .perform(
            post(SignupController.SIGNUP_URL)
                .param("termsAccepted", "true")
                .param("username", uname)
                .param("email", "xxx")
                .param("password", VALID_PWD)
                .param("confirmPassword", VALID_PWD)
                .param("firstName", "pwd")
                .param("lastName", "pwd")
                .param("affiliation", EXPECTED_AFFILIATION))
        .andExpect(model().hasNoErrors());

    // check is persisted OK
    assertEquals(EXPECTED_AFFILIATION, userMgr.getUserByUsername(uname).getAffiliation());
  }

  @ParameterizedTest
  @CsvSource({
    "/signup,",
    "/signup,false",
    "/externalAuth/ajax/signup,",
    "/externalAuth/ajax/signup,false",
    "/signup,true"
  })
  void communitySignupRequiresConsent(String endpoint, String consent) throws Exception {
    String username = getRandomAlphabeticString("consent");
    String email = username + "@example.org";
    MockHttpServletRequestBuilder request =
        post(endpoint)
            .param("username", username)
            .param("email", email)
            .param("password", VALID_PWD)
            .param("confirmPassword", VALID_PWD)
            .param("firstName", "Consent")
            .param("lastName", "Test")
            .param("affiliation", "University of Edinburgh")
            .param("idTokenString", "invalid-test-token")
            .param("clientId", "test-client");
    if (consent != null) {
      request.param("termsAccepted", consent);
    }
    var result = mockMvc.perform(request).andReturn();
    if ("true".equals(consent)) {
      assertTrue(userMgr.userExists(username));
    } else {
      assertFalse(userMgr.userExists(username));
      assertTrue(userMgr.getUserByEmail(email).isEmpty());
      if ("/signup".equals(endpoint)) {
        assertEquals("signup", result.getModelAndView().getViewName());
        BindingResult errors =
            (BindingResult)
                result.getModelAndView().getModel().get(BindingResult.MODEL_KEY_PREFIX + "user");
        assertEquals("errors.signup.termsRequired", errors.getGlobalError().getCode());
      } else {
        assertEquals(
            List.of("Please agree to the terms and conditions before signing up."),
            getErrorListFromAjaxReturnObject(result).getErrorMessages());
      }
    }
    verifyNoInteractions(externalAuthVerifier);
  }
}
