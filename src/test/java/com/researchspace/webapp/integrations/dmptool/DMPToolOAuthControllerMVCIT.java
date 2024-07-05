package com.researchspace.webapp.integrations.dmptool;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.dmptool.client.DMPToolClient;
import com.researchspace.dmptool.client.DMPToolClientImpl;
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
  private URL testBaseUrl;

  @Value("${dmptool.base.url}")
  private URL realBaseUrl;

  @Value("${dmptool.realConnectionTest.client.id}")
  private String clientId;

  @Value("${dmptool.realConnectionTest.client.secret}")
  private String clientSecret;

  private RestTemplate restTemplate;
  private @Autowired DMPToolDMPProvider dmpToolProvider;
  private DMPToolClient dmpToolClient;
  private String jsonDmpTool =
      "{\n"
          + "  \"application\": \"DMPTool\",\n"
          + "  \"api_version\": 2,\n"
          + "  \"source\": \"GET /api/v2/plans/81673\",\n"
          + "  \"time\": \"2024-06-25T10:56:21Z\",\n"
          + "  \"caller\": \"rspace\",\n"
          + "  \"code\": 200,\n"
          + "  \"message\": \"OK\",  \"page\": 1,\n"
          + "  \"per_page\": 100,\n"
          + "  \"total_items\": 1,\n"
          + "  \"items\": [\n"
          + "    {\n"
          + "      \"dmp\": {\n"
          + "        \"title\": \"R Principal Testing DMP integration\",\n"
          + "        \"description\": \"<p>Testing integration with DMP for conference in"
          + " September</p>\",\n"
          + "        \"language\": \"eng\",\n"
          + "        \"created\": \"2022-08-17T10:20:43Z\",\n"
          + "        \"modified\": \"2024-06-18T12:10:27Z\",\n"
          + "        \"ethical_issues_exist\": \"no\",\n"
          + "        \"ethical_issues_description\": \"\",\n"
          + "        \"ethical_issues_report\": \"\",\n"
          + "        \"dmp_id\": {\n"
          + "          \"type\": \"doi\",\n"
          + "          \"identifier\": \"https://doi.org/10.48321/D18W72\"\n"
          + "        },\n"
          + "        \"contact\": {\n"
          + "          \"name\": \"Operations ResearchSpace\",\n"
          + "          \"mbox\": \"operations@researchspace.com\",\n"
          + "          \"dmproadmap_affiliation\": {\n"
          + "            \"name\": \"ResearchSpace\"\n"
          + "          },\n"
          + "          \"contact_id\": {\n"
          + "            \"type\": \"orcid\",\n"
          + "            \"identifier\": \"https://orcid.org/0000-0002-9561-5942\"\n"
          + "          }\n"
          + "        },\n"
          + "        \"project\": [\n"
          + "          {\n"
          + "            \"title\": \"R Principal Testing DMP integration\",\n"
          + "            \"description\": \"<p>Testing integration with DMP for conference in"
          + " September</p>\",\n"
          + "            \"start\": \"2021-09-09T00:00:00Z\",\n"
          + "            \"end\": \"2021-09-22T00:00:00Z\",\n"
          + "            \"funding\": [\n"
          + "              {\n"
          + "                \"name\": \"NHS Lothian (nhslothian.scot.nhs.uk)\",\n"
          + "                \"funder_id\": {\n"
          + "                  \"type\": \"ror\",\n"
          + "                  \"identifier\": \"https://ror.org/03q82t418\"\n"
          + "                },\n"
          + "                \"funding_status\": \"planned\",\n"
          + "                \"dmproadmap_funded_affiliations\": [\n"
          + "                  {\n"
          + "                    \"name\": \"ResearchSpace\"\n"
          + "                  }\n"
          + "                ]\n"
          + "              }\n"
          + "            ]\n"
          + "          }\n"
          + "        ],\n"
          + "        \"dataset\": [\n"
          + "          {\n"
          + "            \"type\": \"dataset\",\n"
          + "            \"title\": \"Generic dataset\",\n"
          + "            \"description\": \"No individual datasets have been defined for this"
          + " DMP.\",\n"
          + "            \"keyword\": [\n"
          + "              \"Computer and information sciences\",\n"
          + "              \"1.2 - Computer and information sciences\"\n"
          + "            ]\n"
          + "          }\n"
          + "        ],\n"
          + "        \"dmproadmap_template\": {\n"
          + "          \"id\": \"1936\",\n"
          + "          \"title\": \"Digital Curation Centre\"\n"
          + "        },\n"
          + "        \"dmproadmap_featured\": \"0\",\n"
          + "        \"dmproadmap_external_system_identifier\":"
          + " \"https://doi.org/10.48321/D18W72\",\n"
          + "        \"dmproadmap_related_identifiers\": [\n"
          + "          {\n"
          + "            \"descriptor\": \"documents\",\n"
          + "            \"type\": \"doi\",\n"
          + "            \"identifier\":"
          + " \"https://demo.dataverse.org//dataset.xhtml?persistentId=doi:10.70122/FK2/KEJZHJ\",\n"
          + "            \"work_type\": \"dataset\"\n"
          + "          },\n"
          + "          {\n"
          + "            \"descriptor\": \"documents\",\n"
          + "            \"type\": \"doi\",\n"
          + "            \"identifier\":"
          + " \"https://demo.dataverse.org//dataset.xhtml?persistentId=doi:10.70122/FK2/CMF6SA\",\n"
          + "            \"work_type\": \"dataset\"\n"
          + "          },\n"
          + "          {\n"
          + "            \"descriptor\": \"documents\",\n"
          + "            \"type\": \"url\",\n"
          + "            \"identifier\":"
          + " \"https://dryad-stg.cdlib.org/stash/dataset/doi%3A10.7959%2Fdryad.vmcvdncp8/c6CxVldzK0CfNA\",\n"
          + "            \"work_type\": \"dataset\"\n"
          + "          }\n"
          + "        ],\n"
          + "        \"dmproadmap_privacy\": \"private\",\n"
          + "        \"dmproadmap_links\": {\n"
          + "          \"get\": \"https://https/api/v2/plans/81673\"\n"
          + "        }\n"
          + "      }\n"
          + "    }\n"
          + "  ]\n"
          + "}";

  @Before
  public void setup() throws Exception {
    super.setUp();
    this.restTemplate = new RestTemplate();
    dmpToolClient = new DMPToolClientImpl(new URL(realBaseUrl, "/api/v2/"));
  }

  private DMPToolDMP mkDMP(Long id, String title) {
    var dmp = new DMPToolDMP();
    dmp.setTitle(title);
    dmp.setLinks(
        Map.of(
            "get", testBaseUrl.toString() + "/api/v2/plans/" + id,
            "download", testBaseUrl.toString() + "/api/v2/plans/" + id + ".pdf"));
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
            (new URL(testBaseUrl, "/oauth/token")).toURI(),
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
    DMPList plansList =
        dmpToolProvider.listPlans(DMPPlanScope.MINE, clientCredentialToken.getAccessToken());
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
    DMPUser dmpUser =
        dmpToolProvider.doJsonDownload(dmp, "A title", clientCredentialToken.getAccessToken());
    assertEquals("A title.json", dmpUser.getDmpDownloadPdf().getName());
    int finalDocCount = getCountOfEntityTable("EcatDocumentFile").intValue();
    assertEquals(initialDocCount + 1, finalDocCount);
    assertEquals(initialDMPUserCount + 1, getCountOfEntityTable("DMPUser"));

    // downloading same dmp for same user again is rejected
    DMPUser dmpUser2 =
        dmpToolProvider.doJsonDownload(dmp, "A title", clientCredentialToken.getAccessToken());
    assertNull(dmpUser2);
    assertEquals(initialDMPUserCount + 1, getCountOfEntityTable("DMPUser"));

    // create another user and token, this can be saved
    RSpaceTestUtils.logout();
    createInitAndLoginAnyUser();
    dmpUser2 =
        dmpToolProvider.doJsonDownload(dmp, "A title", clientCredentialToken.getAccessToken());
    assertNotNull(dmpUser2);
    assertEquals(initialDMPUserCount + 2, getCountOfEntityTable("DMPUser"));
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void getPlanById() throws Exception {
    DMPToolOAuthController.AccessToken clientCredentialToken = getClientCredentialToken();
    var planById =
        dmpToolProvider.getPlanById(PUBLIC_DMP_ID + "", clientCredentialToken.getAccessToken());
    assertEquals(PUBLIC_DMP_TITLE, planById.getTitle());
  }

  @Test
  public void testSanitizeDMPLinksFromDMPToolDMP() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();

    // this also tests that the JSON deserialization works correctly
    DMPToolDMP dmpPlan = mapper.readValue(jsonDmpTool, DMPList.class).getItems().get(0);

    assertTrue(dmpPlan.getLinks().get("get").contains("https://https/api/v2/plans/"));
    dmpPlan = ((DMPToolDMPProviderImpl) dmpToolProvider).sanitizeDMPLinks(dmpPlan);
    assertFalse(dmpPlan.getLinks().get("get").contains("https://https/api/v2/plans/"));
    assertTrue(dmpPlan.getLinks().get("get").contains(realBaseUrl.getHost()));
  }

  @Test
  public void testSanitizeDMPLinksFromJson() {
    assertTrue(jsonDmpTool.contains("https://https/api/v2/plans/"));
    String sanitizedJson = ((DMPToolDMPProviderImpl) dmpToolProvider).sanitizeDMPLinks(jsonDmpTool);
    assertFalse(sanitizedJson.contains("https://https/api/v2/plans/"));
    assertTrue(sanitizedJson.contains(realBaseUrl.getHost()));
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
    dmpToolProvider.addDoiIdentifierToDMP(
        "https://dmptool-stg.cdlib.org/api/v2/plans/" + PUBLIC_DMP_ID,
        doiIdentifier,
        clientCredentialToken.getAccessToken());

    // verify DOI identifier present in updated plan
    String testPlanJson =
        dmpToolProvider.doGet(
            clientCredentialToken.getAccessToken(), "plans/" + PUBLIC_DMP_ID, String.class);
    assertTrue(testPlanJson.contains(doiIdentifier), "no expected DOI in plan: " + testPlanJson);
  }
}
