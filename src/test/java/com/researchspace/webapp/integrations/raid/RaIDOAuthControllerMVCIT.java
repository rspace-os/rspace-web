package com.researchspace.webapp.integrations.raid;

import static java.net.URLEncoder.encode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.model.User;
import com.researchspace.raid.model.exception.RaIDException;
import com.researchspace.service.raid.RaIDServiceClientAdapter;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller.AccessToken;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class RaIDOAuthControllerMVCIT extends API_MVC_TestBase {

  public static final String RAID_EXCEPTION_MESSAGE = "RaidExceptionMessage";
  public static final String AUTHORIZATION_ERROR_PAGE = "connect/authorizationError";
  public static final String ERROR_PAGE = "error";
  @Autowired private RaIDOAuthController raIDOAuthController;
  @Mock private RaIDServiceClientAdapter mockedRaidClientAdapter;

  private static final String SERVER_ALIAS = "DEMO";
  private static final String AUTH_CODE = "authCodeReceived";
  private static final String URL_CONNECTED = "connect/raid/connected";

  private User user;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.openMocks(this);
    user = createInitAndLoginAnyUser();
    raIDOAuthController.setRaidServiceClientAdapter(mockedRaidClientAdapter);
  }

  @Test
  public void testConnect() throws Exception {
    when(mockedRaidClientAdapter.performRedirectConnect(eq(SERVER_ALIAS)))
        .thenReturn(
            "https://demo.raid.org/realms/raid/protocol/openid-connect/auth?client_id=rspace&"
                + "redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapps%2Fraid%2Fcallback&"
                + "response_type=code&scope=openid&state=DEMO");

    MvcResult result =
        mockMvc
            .perform(post("/apps/raid/connect/DEMO").principal(user::getUsername))
            .andExpect(status().is(302))
            .andReturn();

    String actualRedirectedUrl = result.getResponse().getRedirectedUrl();
    assertTrue(
        actualRedirectedUrl.contains(
            "https://demo.raid.org/realms/raid/protocol/openid-connect/auth?"));
    assertTrue(actualRedirectedUrl.contains("client_id=rspace"));
    assertTrue(
        actualRedirectedUrl.contains(
            "redirect_uri="
                + encode("http://localhost:8080/apps/raid/callback", StandardCharsets.UTF_8)));
    assertTrue(actualRedirectedUrl.contains("response_type=code"));
    assertTrue(actualRedirectedUrl.contains("scope=openid"));
    assertTrue(actualRedirectedUrl.contains("state=" + SERVER_ALIAS));
  }

  @Test
  public void testCallbackSuccess() throws Exception {
    when(mockedRaidClientAdapter.performCreateAccessToken(
            eq(user.getUsername()), eq(SERVER_ALIAS), eq(AUTH_CODE)))
        .thenReturn(createAccessToken());

    MvcResult result =
        mockMvc
            .perform(
                get("/apps/raid/callback")
                    .principal(user::getUsername)
                    .param("state", SERVER_ALIAS)
                    .param("code", AUTH_CODE))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    assertTrue(result.getResponse().getForwardedUrl().contains(URL_CONNECTED));
    assertFalse(result.getResponse().getForwardedUrl().contains(AUTHORIZATION_ERROR_PAGE));
    assertFalse(result.getResponse().getForwardedUrl().contains(ERROR_PAGE));

    assertEquals(SERVER_ALIAS, result.getModelAndView().getModel().get("serverAlias"));
    assertNull(result.getModelAndView().getModel().get("error"));
  }

  @Test
  public void testCallbackError() throws Exception {
    when(mockedRaidClientAdapter.performCreateAccessToken(
            eq(user.getUsername()), eq(SERVER_ALIAS), eq(AUTH_CODE)))
        .thenThrow(new RaIDException(RAID_EXCEPTION_MESSAGE));

    MvcResult result =
        mockMvc
            .perform(
                get("/apps/raid/callback")
                    .principal(user::getUsername)
                    .param("state", SERVER_ALIAS)
                    .param("code", AUTH_CODE))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    assertFalse(result.getResponse().getForwardedUrl().contains(URL_CONNECTED));
    assertTrue(result.getResponse().getForwardedUrl().contains(AUTHORIZATION_ERROR_PAGE));
    assertFalse(result.getResponse().getForwardedUrl().contains(ERROR_PAGE));

    assertNull(result.getModelAndView().getModel().get("serverAlias"));
    assertTrue(
        ((OauthAuthorizationError) result.getModelAndView().getModel().get("error"))
            .getErrorMsg()
            .contains(RAID_EXCEPTION_MESSAGE));
  }

  @Test
  public void testRefreshTokenSuccess() throws Exception {
    when(mockedRaidClientAdapter.performRefreshToken(eq(user.getUsername()), eq(SERVER_ALIAS)))
        .thenReturn(createAccessToken());

    MvcResult result =
        mockMvc
            .perform(post("/apps/raid/refresh_token/" + SERVER_ALIAS).principal(user::getUsername))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    assertTrue(result.getResponse().getForwardedUrl().contains(URL_CONNECTED));
    assertFalse(result.getResponse().getForwardedUrl().contains(AUTHORIZATION_ERROR_PAGE));
    assertFalse(result.getResponse().getForwardedUrl().contains(ERROR_PAGE));

    assertNull(result.getModelAndView().getModel().get("error"));
  }

  @Test
  public void testRefreshTokenError() throws Exception {
    when(mockedRaidClientAdapter.performRefreshToken(eq(user.getUsername()), eq(SERVER_ALIAS)))
        .thenThrow(new RaIDException(RAID_EXCEPTION_MESSAGE));

    MvcResult result =
        mockMvc
            .perform(post("/apps/raid/refresh_token/" + SERVER_ALIAS).principal(user::getUsername))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    assertFalse(result.getResponse().getForwardedUrl().contains(URL_CONNECTED));
    assertTrue(result.getResponse().getForwardedUrl().contains(AUTHORIZATION_ERROR_PAGE));
    assertFalse(result.getResponse().getForwardedUrl().contains(ERROR_PAGE));

    assertTrue(
        ((OauthAuthorizationError) result.getModelAndView().getModel().get("error"))
            .getErrorMsg()
            .contains(RAID_EXCEPTION_MESSAGE));
  }

  @Test
  public void testDisconnect() throws Exception {
    MvcResult result =
        mockMvc
            .perform(delete("/apps/raid/connect/" + SERVER_ALIAS).principal(user::getUsername))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    assertNull(result.getResponse().getForwardedUrl());
  }

  @Test
  public void testConnectionIsAlive() throws Exception {
    when(mockedRaidClientAdapter.isRaidConnectionAlive(eq(user.getUsername()), eq(SERVER_ALIAS)))
        .thenReturn(true);

    MvcResult result =
        mockMvc
            .perform(
                post("/apps/raid/test_connection/" + SERVER_ALIAS).principal(user::getUsername))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    assertTrue(raIDOAuthController.isConnectionAlive(SERVER_ALIAS, () -> user.getUsername()));
  }

  @Test
  public void testConnectionIsNotAlive() throws Exception {
    when(mockedRaidClientAdapter.isRaidConnectionAlive(eq(user.getUsername()), eq(SERVER_ALIAS)))
        .thenReturn(false);

    MvcResult result =
        mockMvc
            .perform(
                post("/apps/raid/test_connection/" + SERVER_ALIAS).principal(user::getUsername))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    assertFalse(raIDOAuthController.isConnectionAlive(SERVER_ALIAS, () -> user.getUsername()));
  }

  private AccessToken createAccessToken() {
    AccessToken accessToken = new AccessToken();
    accessToken.setAccessToken("ACCESS_TOKEN");
    accessToken.setRefreshToken("REFRESH_TOKEN");
    accessToken.setExpiresIn(299L);
    return accessToken;
  }
}
