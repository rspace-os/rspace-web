package com.researchspace.webapp.integrations.dmptool;

import static com.researchspace.service.IntegrationsHandler.DMPTOOL_APP_NAME;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.dmptool.model.DMPList;
import com.researchspace.dmptool.model.DMPPlanScope;
import com.researchspace.dmptool.model.DMPToolDMP;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/apps/dmptool")
public class DMPToolOAuthController extends BaseOAuth2Controller {

  @Value("${dmptool.client.id}")
  private String clientId;

  @Value("${dmptool.client.secret}")
  private String clientSecret;

  @Value("${dmptool.base.url}")
  private URL baseUrl;

  @Value("${dmptool.callback.base.url}")
  private String callbackBaseUrl;

  private final RestTemplate restTemplate;

  private @Autowired DMPToolDMPProvider client;

  public DMPToolOAuthController() {
    this.restTemplate = new RestTemplate();
  }

  @Data
  public static class AccessToken {
    private @JsonProperty("access_token") String accessToken;
    private @JsonProperty("token_type") String type;
    private @JsonProperty("created_at") Long createdAt;
    private @JsonProperty("expires_in") Long expiresIn;
    private String scope;
  }

  @PostMapping("/connect")
  public RedirectView connect() throws MalformedURLException {
    String redirectUrl = String.valueOf(new URL(getServerUrl(), "/apps/dmptool/callback"));
    String pathAndQuery =
        String.format(
            "/oauth/authorize?client_id=%s&redirect_uri=%s&response_type=code&scope=read_dmps+edit_dmps",
            clientId, URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8));
    String dmptoolAuthUrl = String.valueOf(new URL(baseUrl, pathAndQuery));
    return new RedirectView(dmptoolAuthUrl);
  }

  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    int deletedConnCount =
        userConnectionManager.deleteByUserAndProvider(DMPTOOL_APP_NAME, principal.getName());
    log.info("Deleted {} DMPTool connection(s) for user {}", deletedConnCount, principal.getName());
  }

  @GetMapping("/callback")
  public String callback(@RequestParam Map<String, String> params, Model model, Principal principal)
      throws IOException, URISyntaxException {

    AccessToken accessToken;
    try {
      accessToken = getAccessToken(params.get("code"));
    } catch (HttpStatusCodeException e) {
      OauthAuthorizationError error =
          OauthAuthorizationError.builder()
              .appName("DMPTool")
              .errorMsg("Exception during token exchange")
              .errorDetails(e.getResponseBodyAsString())
              .build();
      model.addAttribute("error", error);

      return "connect/authorizationError";
    }

    UserConnection conn = new UserConnection();
    conn.setAccessToken(accessToken.accessToken);
    conn.setExpireTime(getExpireTime(accessToken.expiresIn));
    conn.setDisplayName("DMPTool access token");
    conn.setId(
        new UserConnectionId(principal.getName(), DMPTOOL_APP_NAME, "ProviderUserIdNotNeeded"));
    userConnectionManager.save(conn);
    log.info("Connected DMPTool for user {}", principal.getName());

    return "connect/dmptool/connected";
  }

  private URL getServerUrl() throws MalformedURLException {
    if (StringUtils.isEmpty(callbackBaseUrl)) {
      return new URL(properties.getServerUrl());
    } else {
      return new URL(callbackBaseUrl);
    }
  }

  private long getExpireTime(Long expiresIn) {
    return Instant.now().toEpochMilli() + (expiresIn * 1000);
  }

  private AccessToken getAccessToken(String authorizationCode)
      throws HttpStatusCodeException, MalformedURLException {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "authorization_code");
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);
    formData.add("code", authorizationCode);
    formData.add("redirect_uri", String.valueOf(new URL(getServerUrl(), "/apps/dmptool/callback")));
    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

    return restTemplate
        .exchange(baseUrl + "/oauth/token", HttpMethod.POST, request, AccessToken.class)
        .getBody();
  }

  /**
   * Returns access token to the UI. Status is {@code}false{@code} if there is no access token
   * stored.
   *
   * @param principal
   * @return
   */
  @GetMapping("/access_token")
  @ResponseBody
  public ServiceOperationResult<String> getAccessToken(Principal principal) {
    Optional<UserConnection> optConn = getUserConnection(principal);

    if (!optConn.isPresent()) {
      // front-end checks if DMPTool app has OAuth connected before the list_dmps
      // operation. Hence this is unlikely to happen.
      log.error("No DMPtool connection found for user {}", principal.getName());
      return ServiceOperationResult.fromOptionalError(
          Optional.of("Access token isn't enabled - user must connect in Apps page"));
    }
    return new ServiceOperationResult<String>(optConn.get().getAccessToken(), true);
  }

  @PostMapping("/pdfById/{id}")
  @ResponseBody
  public AjaxReturnObject<Boolean> getPdfById(@PathVariable("id") Integer id) {

    User user = userManager.getAuthenticatedUserInSession();
    try {
      ServiceOperationResult<DMPToolDMP> dmpDetails = client.getPlanById(id + "", user);
      if (dmpDetails.isSucceeded()) {
        var dmpUserServiceOperationResult =
            client.doJsonDownload(dmpDetails.getEntity(), dmpDetails.getEntity().getTitle(), user);
        if (!dmpUserServiceOperationResult.isSucceeded()) {
          return new AjaxReturnObject<>(
              null,
              ErrorList.of(
                  "Couldn't download DMP pdf for id: "
                      + id
                      + ". "
                      + dmpUserServiceOperationResult.getMessage()));
        }
      } else {
        return new AjaxReturnObject<>(
            null, ErrorList.of("Couldn't get details of DMP with id: " + id));
      }

    } catch (URISyntaxException | IOException | RuntimeException e) {
      log.error("Failure on downloading DMP pdf", e);
      return new AjaxReturnObject<>(null, ErrorList.of("Couldn't download DMP pdf for id: " + id));
    }
    return new AjaxReturnObject<Boolean>(Boolean.TRUE, null);
  }

  @GetMapping("/plans")
  @ResponseBody
  public AjaxReturnObject<DMPList> listDMPs(
      @RequestParam(name = "scope", required = false) String scopeParam) {

    DMPPlanScope scope = DMPPlanScope.MINE;
    if ("public".equalsIgnoreCase(scopeParam) || "both".equalsIgnoreCase(scopeParam)) {
      scope = DMPPlanScope.valueOf(scopeParam.toUpperCase());
    }

    User user = userManager.getAuthenticatedUserInSession();
    try {
      ServiceOperationResult<DMPList> result = client.listPlans(scope, user);
      if (result.isSucceeded()) {
        return new AjaxReturnObject<>(result.getEntity(), null);
      } else {
        return new AjaxReturnObject<>(null, ErrorList.of(result.getMessage()));
      }
    } catch (MalformedURLException | URISyntaxException e) {
      log.error("Failure on listing DMPs", e);
      return new AjaxReturnObject<>(null, ErrorList.of("Couldn't list DMPs"));
    }
  }

  @GetMapping("/baseUrlHost")
  @ResponseBody
  public String getDmpServerUrl() {
    return baseUrl.getHost();
  }

  @PostMapping("/addDoiIdentifierToDmp")
  @ResponseBody
  public void attachDoiToDmpId(
      @RequestParam("doi") String doi, @RequestParam("dmpId") String dmpId) {

    log.info("doi: " + doi);
    log.info("dmpId: " + dmpId);

    User user = userManager.getAuthenticatedUserInSession();
    client.addDoiIdentifierToDMP(dmpId, doi, user);
  }

  private Optional<UserConnection> getUserConnection(Principal principal) {
    Optional<UserConnection> optConn =
        userConnectionManager.findByUserNameProviderName(principal.getName(), DMPTOOL_APP_NAME);
    return optConn;
  }
}
