package com.researchspace.webapp.integrations.dcd;

import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.dmptool.model.DMPList;
import com.researchspace.dmptool.model.DMPPlanScope;
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
import java.net.URL;
import java.security.Principal;
import java.time.Instant;
import java.util.Optional;
import lombok.Data;
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
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/apps/digitalcommonsdata")
public class DigitalCommonsDataController extends BaseOAuth2Controller {

  @Value("${dcd.client.id}")
  private String clientId;

  @Value("${dcd.client.secret}")
  private String clientSecret;

  @Value("${dcd.auth.base.url}")
  private URL authBaseUrl;

  @Value("${dcd.api.base.url}")
  private URL apiBaseUrl;

  @Value("${dcd.callback.base.url}")
  private String callbackBaseUrl;

  private final RestTemplate restTemplate;

  private @Autowired DigitalCommonsDataManager digitalCommonsDataManager;

  public DigitalCommonsDataController() {
    this.restTemplate = new RestTemplate();
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class AccessToken {
    private @JsonProperty("access_token") String accessToken;
    private @JsonProperty("token_type") String type;
    private @JsonProperty("created_at") Long createdAt;
    private @JsonProperty("expires_in") Long expiresIn;
    private String scope;
  }

  @PostMapping("/connect")
  public String connect(Model model, Principal principal) throws MalformedURLException {
    AccessToken accessToken;
    try {
      accessToken = getAccessToken();
    } catch (HttpStatusCodeException e) {
      OauthAuthorizationError error =
          OauthAuthorizationError.builder()
              .appName("DigitalCommonsData")
              .errorMsg("Exception during token exchange")
              .errorDetails(e.getResponseBodyAsString())
              .build();
      model.addAttribute("error", error);

      return "connect/authorizationError";
    }

    UserConnection conn = new UserConnection();
    conn.setAccessToken(accessToken.accessToken);
    conn.setExpireTime(getExpireTime(accessToken.expiresIn));
    conn.setDisplayName("DigitalCommonsData access token");
    conn.setId(
        new UserConnectionId(
            principal.getName(), DIGITAL_COMMONS_DATA_APP_NAME, "ProviderUserIdNotNeeded"));
    userConnectionManager.save(conn);
    log.info("Connected DigitalCommonsData for user {}", principal.getName());

    return "connect/dcd/connected";
  }

  @DeleteMapping("/connect")
  public void disconnect(Principal principal) {
    int deletedConnCount =
        userConnectionManager.deleteByUserAndProvider(
            DIGITAL_COMMONS_DATA_APP_NAME, principal.getName());
    log.info(
        "Deleted {} DigitalCommonsData connection(s) for user {}",
        deletedConnCount,
        principal.getName());
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
      // front-end checks if DigitalCommonsData app has OAuth connected before the list_dcds
      // operation. Hence this is unlikely to happen.
      log.error("No DigitalCommonsData connection found for user {}", principal.getName());
      return ServiceOperationResult.fromOptionalError(
          Optional.of("Access token isn't enabled - user must connect in Apps page"));
    }
    return new ServiceOperationResult<>(optConn.get().getAccessToken(), true);
  }

  @PostMapping("/profileImage/upload")
  @ResponseBody
  public void postProfileImage(
      @RequestParam("imageFile") MultipartFile imageFile, Principal subject) throws IOException {

    //    final int maxWidth = 124;
    //    final int size = 1000;
    //
    //    if (imageFile.getSize() > MAX_PROFILE_SIZE) {
    //      throw new IOException(
    //          "Profile image size is too big - must be smaller than " + MAX_PROFILE_SIZE);
    //    }
    //    String extension = MediaUtils.getExtension(imageFile.getOriginalFilename());
    //    if (!MediaUtils.isImageFile(extension)) {
    //      log.warn("Attempting to save a possible non-image file with extension[{}]", extension);
    //      throwUnacceptableImage(imageFile);
    //    }
    //
    //    Optional<BufferedImage> scaledImage =
    //        ImageUtils.scale(
    //            imageFile.getInputStream(), maxWidth,
    // getExtension(imageFile.getOriginalFilename()));
    //    if (!scaledImage.isPresent()) {
    //      log.warn("Couldn't scale image [{}]", imageFile.getOriginalFilename());
    //      throwUnacceptableImage(imageFile);
    //    }
    //    ByteArrayOutputStream baos = new ByteArrayOutputStream(size);
    //
    //    ImageIO.write(scaledImage.get(), "png", baos);
    //    baos.flush();
    //
    //    User user = userManager.getUserByUsername(subject.getName());
    //    UserProfile up = userProfileManager.getUserProfile(user);
    //    ImageBlob image = new ImageBlob(baos.toByteArray());
    //    up.setProfilePicture(image);
    //    userProfileManager.saveUserProfile(up);

  }

  @PostMapping("/datasetById/{id}")
  @ResponseBody
  public AjaxReturnObject<Boolean> getDatasetById(@PathVariable("id") Integer id) {

    User user = userManager.getAuthenticatedUserInSession();
    //    try {
    //      DcdModel dcdDetails = digitalCommonsDataManager.getPlanById(id + "", user);
    //    //TODO[nik]: then save it
    //
    //      if (dcdDetails.isSucceeded()) {
    //        var dcdUserServiceOperationResult =
    //            digitalCommonsDataManagerImpl.doJsonDownload(dcdDetails.getEntity(),
    //                dcdDetails.getEntity().getTitle(), user);
    //        if (!dcdUserServiceOperationResult.isSucceeded()) {
    //          return new AjaxReturnObject<>(
    //              null,
    //              ErrorList.of(
    //                  "Couldn't download DCD pdf for id: "
    //                      + id
    //                      + ". "
    //                      + dcdUserServiceOperationResult.getMessage()));
    //        }
    //      } else {
    //        return new AjaxReturnObject<>(
    //            null, ErrorList.of("Couldn't get details of DCD with id: " + id));
    //      }
    //
    //    } catch (URISyntaxException | IOException | RuntimeException e) {
    //      log.error("Failure on downloading DCD json", e);
    //      return new AjaxReturnObject<>(null, ErrorList.of("Couldn't download DCD json for id: " +
    // id));
    //    }
    return new AjaxReturnObject<>(Boolean.TRUE, null);
  }

  @GetMapping("/datasets")
  @ResponseBody
  public AjaxReturnObject<DMPList> listDCDs(
      @RequestParam(name = "scope", required = false) String scopeParam) {

    DMPPlanScope scope = DMPPlanScope.MINE;
    if ("public".equalsIgnoreCase(scopeParam) || "both".equalsIgnoreCase(scopeParam)) {
      scope = DMPPlanScope.valueOf(scopeParam.toUpperCase());
    }

    User user = userManager.getAuthenticatedUserInSession();
    try {
      ServiceOperationResult<DMPList> result = digitalCommonsDataManager.listPlans(scope, user);
      if (result.isSucceeded()) {
        return new AjaxReturnObject<>(result.getEntity(), null);
      } else {
        return new AjaxReturnObject<>(null, ErrorList.of(result.getMessage()));
      }
    } catch (Exception e) {
      log.error("Failure on listing DCDs", e);
      return new AjaxReturnObject<>(null, ErrorList.of("Couldn't list DCDs"));
    }
  }

  @GetMapping("/baseAuthUrlHost")
  @ResponseBody
  public String getDcdAuthServerUrl() {
    return authBaseUrl.getHost();
  }

  @GetMapping("/baseApiUrlHost")
  @ResponseBody
  public String getDcdApiServerUrl() {
    return apiBaseUrl.getHost();
  }

  @PostMapping("/addDoiIdentifierToDcd")
  @ResponseBody
  public void attachDoiToDmpId(
      @RequestParam("doi") String doi, @RequestParam("dcdId") String dcdId) {

    log.info("doi: " + doi);
    log.info("dcdId: " + dcdId);

    User user = userManager.getAuthenticatedUserInSession();
    digitalCommonsDataManager.addDoiIdentifierToDMP(dcdId, doi, user);
  }

  private Optional<UserConnection> getUserConnection(Principal principal) {
    Optional<UserConnection> optConn =
        userConnectionManager.findByUserNameProviderName(
            principal.getName(), DIGITAL_COMMONS_DATA_APP_NAME);
    return optConn;
  }

  private long getExpireTime(Long expiresIn) {
    return Instant.now().toEpochMilli() + (expiresIn * 1000);
  }

  private AccessToken getAccessToken() throws HttpStatusCodeException {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setBasicAuth(clientId, clientSecret);
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "client_credentials");
    formData.add("scope", "openid profile email dcd:profile");
    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

    // returns access token
    return restTemplate
        .exchange(authBaseUrl + "/oauth2/token", HttpMethod.POST, request, AccessToken.class)
        .getBody();
  }
}
