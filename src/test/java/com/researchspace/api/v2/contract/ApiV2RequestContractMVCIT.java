package com.researchspace.api.v2.contract;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * Credentials, statelessness, cross-origin rules and rate-limit metadata: everything that happens
 * to a v2 request before and after the collection layer sees it.
 *
 * <p>Corresponds to PayloadCMS's {@code test/auth}, restricted to what this API actually offers. v2
 * has no login, logout, refresh or cookie-based session, which is the point of most of these
 * assertions.
 *
 * <p>{@link ApiV2StatelessRequestFilter} is a servlet filter and therefore absent from a plain
 * {@code webAppContextSetup} chain, so the statelessness block builds its own {@code MockMvc} with
 * the filter installed.
 */
@ApiV2WebIntegrationTest
@DisplayName("REST API v2 request contract")
class ApiV2RequestContractMVCIT {

  private static final String ME = "/api/v2/users/me";
  private static final String CONFIG = "/api/v2/config";
  private static final String MAINTENANCES = "/api/v2/maintenances";

  @Autowired private WebApplicationContext context;

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
  }

  @AfterEach
  void tearDown() {
    fixture.cleanUp();
  }

  @Nested
  @DisplayName("credentials")
  class Credentials {

    @Test
    @DisplayName("an API key identifies the caller")
    void anApiKeyIdentifiesTheCaller() throws Exception {
      mockMvc
          .perform(get(ME).header("apiKey", fixture.userKey()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.username").value(fixture.user().getUsername()))
          .andExpect(jsonPath("$.id").value(fixture.user().getId()))
          .andExpect(jsonPath("$.session").exists());
    }

    @ParameterizedTest(name = "apiKey \"{0}\" is refused")
    @ValueSource(strings = {"not-a-key", "', OR 1=1 --", "0123456789012345678901234567890a"})
    @DisplayName("an unusable API key is 401 with a challenge, whatever its shape")
    void anUnusableApiKeyIsRefused(String key) throws Exception {
      mockMvc
          .perform(get(ME).header("apiKey", key))
          .andExpect(status().isUnauthorized())
          .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    @ParameterizedTest(name = "Authorization \"{0}\" is refused")
    @ValueSource(strings = {"Bearer nonsense", "Bearer", "Basic dXNlcjpwYXNz", "nonsense"})
    @DisplayName("an unusable Authorization header is 401 with a challenge")
    void anUnusableAuthorizationHeaderIsRefused(String authorization) throws Exception {
      mockMvc
          .perform(get(ME).header(HttpHeaders.AUTHORIZATION, authorization))
          .andExpect(status().isUnauthorized())
          .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    /**
     * The design states the server uses the API key when both credential headers are present. Worth
     * pinning: the opposite precedence would make a stale {@code Authorization} header silently win
     * over the key a client just supplied.
     */
    @Test
    @DisplayName("the API key wins when both credential headers are present")
    void theApiKeyWinsWhenBothArePresent() throws Exception {
      mockMvc
          .perform(
              get(ME)
                  .header("apiKey", fixture.userKey())
                  .header(HttpHeaders.AUTHORIZATION, "Bearer nonsense"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.username").value(fixture.user().getUsername()));
    }

    /** A public route must still reject credentials that were supplied and are invalid. */
    @Test
    @DisplayName("invalid credentials are refused even on a public route")
    void invalidCredentialsAreRefusedEvenOnAPublicRoute() throws Exception {
      mockMvc.perform(get(CONFIG)).andExpect(status().isOk());
      mockMvc
          .perform(get(CONFIG).header("apiKey", "not-a-key"))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("statelessness")
  class Statelessness {

    @Test
    @DisplayName("a browser session cookie alone does not authenticate a v2 request")
    void aSessionCookieAloneDoesNotAuthenticate() throws Exception {
      mockMvc
          .perform(get(ME).cookie(new Cookie("JSESSIONID", "whatever")))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an API key still works alongside a stripped cookie")
    void anApiKeyStillWorksAlongsideAStrippedCookie() throws Exception {
      mockMvc
          .perform(
              get(ME).header("apiKey", fixture.userKey()).cookie(new Cookie("JSESSIONID", "x")))
          .andExpect(status().isOk());
    }

    /** No v2 route may hand a client a servlet session it did not already have. */
    @Test
    @DisplayName("a v2 request does not create a session")
    void aV2RequestDoesNotCreateASession() throws Exception {
      assertNull(
          mockMvc
              .perform(get(MAINTENANCES).header("apiKey", fixture.userKey()))
              .andReturn()
              .getRequest()
              .getSession(false));
    }
  }

  @Nested
  @DisplayName("cross-origin requests")
  class CrossOrigin {

    /** The dev deployment sets {@code api.permissiveCors.enabled=true}. */
    @Test
    @DisplayName("a browser preflight advertises the v2 methods and credential headers")
    void aPreflightAdvertisesTheV2MethodsAndHeaders() throws Exception {
      mockMvc
          .perform(
              options(MAINTENANCES)
                  .header(HttpHeaders.ORIGIN, "https://example.org")
                  .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                  // Asked for explicitly: Spring echoes allowed headers only for the ones the
                  // preflight requests, so omitting this would assert an absent header, not a
                  // forbidden one.
                  .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "apiKey, Content-Type"))
          .andExpect(status().is2xxSuccessful())
          .andExpect(
              header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, Matchers.notNullValue()))
          .andExpect(
              header()
                  .string(
                      HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, Matchers.containsString("PATCH")))
          .andExpect(
              header()
                  .string(
                      HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, Matchers.containsString("DELETE")))
          .andExpect(
              header()
                  .string(
                      HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, Matchers.containsString("apiKey")));
    }

    /**
     * A preflight is what a browser sends before any cross-origin write or any request carrying the
     * {@code apiKey} header, so this is the assertion that decides whether permissive CORS is real.
     * It used to answer 403: the policy was a {@code HandlerInterceptor}, and Spring decides a
     * preflight from the registered {@code CorsConfiguration}, so headers set later by an
     * interceptor do not change that decision. It is now declared through {@code addCorsMappings}.
     */
    @Test
    @DisplayName("a preflight for a write method is allowed, not refused")
    void aPreflightForAWriteMethodIsAllowed() throws Exception {
      mockMvc
          .perform(
              options(MAINTENANCES)
                  .header(HttpHeaders.ORIGIN, "https://example.org")
                  .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE")
                  .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "apiKey"))
          .andExpect(status().is2xxSuccessful())
          .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
    }

    /**
     * A simple cross-origin request still gets the permissive header, since no preflight occurs.
     */
    @Test
    @DisplayName("a simple cross-origin GET does receive the permissive allow-origin header")
    void aSimpleCrossOriginGetReceivesAllowOrigin() throws Exception {
      mockMvc
          .perform(get(MAINTENANCES).header(HttpHeaders.ORIGIN, "https://example.org"))
          .andExpect(status().isOk())
          .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
    }

    /**
     * The token route mints a credential from the live browser session, so permissive API CORS must
     * not extend to it. A permissive allow-origin here would make the token reachable cross-site.
     */
    @Test
    @DisplayName("permissive CORS does not apply to the token-minting route")
    void permissiveCorsDoesNotApplyToTheTokenRoute() throws Exception {
      mockMvc
          .perform(
              options("/api/v2/oauth/tokens")
                  .header(HttpHeaders.ORIGIN, "https://example.org")
                  .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
          .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("the token route ignores an API key")
    void theTokenRouteIgnoresAnApiKey() throws Exception {
      mockMvc
          .perform(post("/api/v2/oauth/tokens").header("apiKey", fixture.userKey()))
          .andExpect(status().is4xxClientError())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }
  }

  @Nested
  @DisplayName("rate limiting")
  class RateLimiting {

    /**
     * The documented deviation from v1: v1 fabricates rate-limit statistics when throttling is off,
     * v2 omits the headers entirely. The dev deployment leaves {@code api.throttling.enabled} at
     * its {@code false} default, so absence is the expected state here.
     */
    @Test
    @DisplayName("no rate-limit headers are sent when throttling is disabled")
    void noRateLimitHeadersWhenThrottlingIsDisabled() throws Exception {
      mockMvc
          .perform(get(MAINTENANCES).header("apiKey", fixture.userKey()))
          .andExpect(status().isOk())
          .andExpect(header().doesNotExist("X-Rate-Limit-Remaining"))
          .andExpect(header().doesNotExist("X-Rate-Limit-Reset"));
    }
  }

  @Nested
  @DisplayName("concrete user routes")
  class ConcreteUserRoutes {

    @Test
    @DisplayName("the current-user route reports identity, capabilities and session state")
    void theCurrentUserRouteReportsIdentityAndSession() throws Exception {
      mockMvc
          .perform(get(ME).header("apiKey", fixture.userKey()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.username").value(fixture.user().getUsername()))
          .andExpect(jsonPath("$.hasSysAdminRole").value(false))
          .andExpect(jsonPath("$.capabilities").exists())
          .andExpect(jsonPath("$.session").exists())
          .andExpect(jsonPath("$.password").doesNotExist())
          .andExpect(
              header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("no-store")));
    }

    /**
     * Pins what a user who has never uploaded an image actually gets, which is 404 rather than a
     * default avatar. Worth knowing before a client points an {@code <img src>} at this route: for
     * a new account the common case is a broken image, not a placeholder.
     */
    @Test
    @DisplayName("the profile image is 404 for a user who has not uploaded one")
    void theProfileImageIs404ForAUserWithoutOne() throws Exception {
      mockMvc
          .perform(get(ME + "/profile-image").header("apiKey", fixture.userKey()))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON));
    }

    @Test
    @DisplayName("the current-user route requires credentials")
    void theCurrentUserRouteRequiresCredentials() throws Exception {
      mockMvc.perform(get(ME)).andExpect(status().isUnauthorized());
      mockMvc.perform(get(ME + "/profile-image")).andExpect(status().isUnauthorized());
    }
  }
}
