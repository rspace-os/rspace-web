package com.researchspace.webapp.integrations.dmptool;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dmptool.model.DMPList;
import com.researchspace.dmptool.model.DMPPlanScope;
import com.researchspace.dmptool.model.DMPToolDMP;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.webapp.controller.MVCTestBase;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@RunWith(ConditionalTestRunner.class)
public class DMPToolOAuthControllerMVCIT extends MVCTestBase {

  // this is created in dev@r.c. test-DMP account but could be any public DMP ID
  private static final Long PUBLIC_DMP_ID = 69563L;
  public static final String PUBLIC_DMP_TITLE = "Testing DMP integration";

  @Value("${dmptool.realConnectionTest.base.url}")
  private URL baseUrl;

  @Value("${dmptool.realConnectionTest.client.id}")
  private String clientId;

  @Value("${dmptool.realConnectionTest.client.secret}")
  private String clientSecret;

  private RestTemplate restTemplate;
  private @Autowired DMPToolDMPProvider client;

  @Before
  public void setup() throws Exception {
    super.setUp();
    this.restTemplate = new RestTemplate();
  }

  private DMPToolDMP mkDMP(Long id, String title) {
    var dmp = new DMPToolDMP();
    dmp.setTitle(title);
    dmp.setLinks(
        Map.of(
            "get", baseUrl.toString() + "/api/v2/plans/" + id,
            "download", baseUrl.toString() + "/api/v2/plans/" + id + ".pdf"));
    return dmp;
  }

  DMPToolOAuthController.AccessToken getClientCredentialToken()
      throws URISyntaxException, MalformedURLException {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "client_credentials");
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);

    /* with grant_type=client_credentials we can only read or create dmps, but not edit them
     * see: https://github.com/CDLUC3/dmptool/wiki/API-Authentication */
    formData.add("scope", "read_dmps"); //

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

    ResponseEntity<DMPToolOAuthController.AccessToken> resp =
        restTemplate.exchange(
            (new URL(baseUrl, "/oauth/token")).toURI(),
            HttpMethod.POST,
            request,
            DMPToolOAuthController.AccessToken.class);
    return resp.getBody();
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void getToken() throws Exception {
    DMPToolOAuthController.AccessToken clientCredentialToken = getClientCredentialToken();
    assertNotNull(clientCredentialToken);
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void doListPlans() throws Exception {
    DMPToolOAuthController.AccessToken clientCredentialToken = getClientCredentialToken();

    // try conversion to API response object
    DMPList plansList = client.listPlans(DMPPlanScope.MINE, clientCredentialToken.getAccessToken());
    assertEquals(5, plansList.getItems().size());
    assertFalse(plansList.getItems().isEmpty());
    DMPToolDMP firstPlan = plansList.getItems().get(0);
    assertNotNull(firstPlan.getId());
    assertNotNull(firstPlan.getTitle());
    assertNotNull(firstPlan.getDescription());
    assertEquals("eng", firstPlan.getLanguage());
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void downloadPublicDMPPlan() throws Exception {
    var dmp = mkDMP(PUBLIC_DMP_ID, PUBLIC_DMP_TITLE);

    int initialDocCount = getCountOfEntityTable("EcatDocumentFile").intValue();
    int initialDMPUserCount = getCountOfEntityTable("DMPUser").intValue();
    DMPToolOAuthController.AccessToken clientCredentialToken = getClientCredentialToken();
    createInitAndLoginAnyUser();
    DMPUser dmpUser = client.doPdfDownload(dmp, "A title", clientCredentialToken.getAccessToken());
    assertEquals("A title.pdf", dmpUser.getDmpDownloadPdf().getName());
    int finalDocCount = getCountOfEntityTable("EcatDocumentFile").intValue();
    assertEquals(initialDocCount + 1, finalDocCount);
    assertEquals(initialDMPUserCount + 1, getCountOfEntityTable("DMPUser"));

    // downloading same dmp for same user again is rejected
    DMPUser dmpUser2 = client.doPdfDownload(dmp, "A title", clientCredentialToken.getAccessToken());
    assertNull(dmpUser2);
    assertEquals(initialDMPUserCount + 1, getCountOfEntityTable("DMPUser"));

    // create another user and token, this can be saved
    RSpaceTestUtils.logout();
    User other = createInitAndLoginAnyUser();
    dmpUser2 = client.doPdfDownload(dmp, "A title", clientCredentialToken.getAccessToken());
    assertNotNull(dmpUser2);
    assertEquals(initialDMPUserCount + 2, getCountOfEntityTable("DMPUser"));
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void getPlanById() throws Exception {
    DMPToolOAuthController.AccessToken clientCredentialToken = getClientCredentialToken();
    var planById = client.getPlanById(PUBLIC_DMP_ID + "", clientCredentialToken.getAccessToken());
    assertEquals(PUBLIC_DMP_TITLE, planById.getTitle());
  }

  /**
   * won't work anymore, as it seem DMP API no longer allows editing with
   * grant_type=client_credentials authentication
   */
  // @Test
  // @RunIfSystemPropertyDefined("nightly")
  public void attachDoiToPublicDMPPlan() throws Exception {
    DMPToolOAuthController.AccessToken clientCredentialToken = getClientCredentialToken();
    String doiIdentifier =
        "https://doi.org/10.987/rsUnitTest-" + DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    client.addDoiIdentifierToDMP(
        "https://dmptool-stg.cdlib.org/api/v2/plans/" + PUBLIC_DMP_ID,
        doiIdentifier,
        clientCredentialToken.getAccessToken());

    // verify DOI identifier present in updated plan
    String testPlanJson =
        client.doGet(
            clientCredentialToken.getAccessToken(), "plans/" + PUBLIC_DMP_ID, String.class);
    assertTrue(testPlanJson.contains(doiIdentifier), "no expected DOI in plan: " + testPlanJson);
  }
}
