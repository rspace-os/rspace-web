package com.researchspace.webapp.integrations.digitalcommonsdata;

import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.dcd.model.DcdAccessToken;
import com.researchspace.dcd.model.DcdDataset;
import com.researchspace.dcd.model.DcdFile;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.UserConnectionManager;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@WebAppConfiguration
public class DigitalCommonsDataMVCIT extends API_MVC_TestBase {

  private @Autowired UserConnectionManager userConnectionManager;
  private User user;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
  }

  @Test
  @Ignore(
      "This test was used for the Digital Commons data POC. "
          + "We leave the test Ignored so we can potentially run it manually")
  public void testGetAccessToken() throws Exception {
    MvcResult result =
        mockMvc
            .perform(post("/apps/digitalcommonsdata/connect").principal(user::getUsername))
            .andExpect(status().is(302))
            .andReturn();

    assertTrue(
        result
            .getResponse()
            .getRedirectedUrl()
            .contains("https://auth.data.mendeley.com/oauth2/authorize?response_type=code"));

    String accessToken =
        userConnectionManager
            .findByUserNameProviderName(user.getUsername(), DIGITAL_COMMONS_DATA_APP_NAME)
            .get()
            .getAccessToken();
    assertNotNull(accessToken);
    assertFalse(accessToken.isBlank());
    assertEquals("TEMP", accessToken);
  }

  @Test
  @Ignore(
      "This test was used for the Digital Commons data POC. We leave the test Ignored so we can"
          + " potentially run it manually by adding clientId and clientSecret")
  public void testGetDatasetsAndByIdCredentialFlow() throws IOException, URISyntaxException {
    RestTemplate restTemplate = new RestTemplate();

    ////////// get access token ////////// - WITH CREDENTIAL FLOW
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setBasicAuth("paste <clientId> here", "paste <clientSecret> here");

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "client_credentials");
    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

    DcdAccessToken dcdAccessToken =
        restTemplate
            .exchange(
                "https://auth.data.mendeley.com/oauth2/token",
                HttpMethod.POST,
                request,
                DcdAccessToken.class)
            .getBody();
    System.out.println("AccessToken: " + dcdAccessToken);

    ////////// get the datasets //////////
    String jsonResult =
        restTemplate
            .exchange(
                new URL("https://api.data.mendeley.com/datasets").toURI(),
                //       new URL("http://localhost:80").toURI(),
                HttpMethod.GET,
                new HttpEntity<>(addAuthorizationHeaders(dcdAccessToken.getAccessToken())),
                String.class)
            .getBody();
    System.out.println("Datasets: " + jsonResult);

    ////////// get the dataset with ID = jxj8bn2vyf //////////
    jsonResult =
        restTemplate
            .exchange(
                "https://api.data.mendeley.com/datasets/jxj8bn2vyf",
                //       new URL("http://localhost:80").toURI(),
                HttpMethod.GET,
                new HttpEntity<>(addAuthorizationHeaders(dcdAccessToken.getAccessToken())),
                String.class)
            .getBody();
    System.out.println("Dataset[jxj8bn2vyf]: " + jsonResult);

    /////// create a draft dataset   ////////
    headers = addAuthorizationHeaders(dcdAccessToken.getAccessToken());
    headers.setContentType(MediaType.APPLICATION_JSON);
    jsonResult =
        restTemplate
            .exchange(
                // "https://api.data.mendeley.com/active-data-entities/datasets/drafts",
                new URL("http://localhost:80").toURI(),
                HttpMethod.POST,
                new HttpEntity<>(
                    "{\n"
                        + "  \"ancestors\": [\n"
                        + "    {\n"
                        + "      \"parent\": {}\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"parent\": {\n"
                        + "    \"parent\": {}\n"
                        + "  },\n"
                        + "  \"empty\": true,\n"
                        + "  \"name\": \"test_nik_code_1\",\n"
                        + "  \"description\": \"test_nik_code_1\",\n"
                        + "  \"method\": \"post\",\n"
                        + "  \"is_confidential\": true,\n"
                        + "  \"property1\": {},\n"
                        + "  \"property2\": {}\n"
                        + "}",
                    headers),
                String.class)
            .getBody();
    System.out.println("Create draft dataset: " + jsonResult);
  }

  @Test
  @Ignore(
      "This test was used for the Digital Commons data POC. "
          + "We leave the test Ignored so we can potentially run it manually by the access token")
  public void testCreateDatasetAndPushFile() throws IOException, URISyntaxException {
    RestTemplate restTemplate = new RestTemplate();

    ////////// get access token ////////// - WITH CREDENTIAL FLOW
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    /* ************
     ///// 1. GET THE ACCESS TOKEN MANUALLY /////
    **************** */
    int randomInt = (int) (Math.random() * 5000 + 1);
    //    /////// create a draft dataset   //////// OK
    String ACCESS_TOKEN = "<Paste ACCESS TOKEN HERE>";
    headers = addAuthorizationHeaders(ACCESS_TOKEN);
    headers.setContentType(MediaType.APPLICATION_JSON);
    DcdDataset datasetResult =
        restTemplate
            .exchange(
                "https://api.data.mendeley.com/active-data-entities/datasets/drafts",
                // new URL("http://localhost:80").toURI(),
                HttpMethod.POST,
                new HttpEntity<>(
                    "{\n"
                        + "  \"ancestors\": [\n"
                        + "    {\n"
                        + "      \"parent\": {}\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"parent\": {\n"
                        + "    \"parent\": {}\n"
                        + "  },\n"
                        + "  \"empty\": true,\n"
                        + "  \"name\": \"test_nik_code_"
                        + randomInt
                        + "\",\n"
                        + "  \"description\": \"test_nik_code_"
                        + randomInt
                        + "\",\n"
                        + "  \"is_confidential\": false"
                        + "}",
                    headers),
                DcdDataset.class)
            .getBody();
    System.out.println("Create draft dataset: " + datasetResult);

    ///// CREATE a file   //////////
    FileSystemResource file =
        new FileSystemResource(new File("src/test/resources/TestResources/Picture1.png"));

    headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentLength(file.contentLength());
    //        headers.setContentDisposition(contentDisposition);

    DcdFile dcdFileResult =
        restTemplate
            .exchange(
                "https://uploads.data.mendeley.com/uploads",
                //        "http://localhost:80"),
                HttpMethod.POST,
                new HttpEntity<>(file, addAuthorizationHeaders(headers, ACCESS_TOKEN)),
                DcdFile.class)
            .getBody();
    System.out.println("Upload file result: " + dcdFileResult);

    ///////// LINK THE FILE TO THE DATASET ///////////
    headers = addAuthorizationHeaders(ACCESS_TOKEN);
    headers.setContentType(MediaType.APPLICATION_JSON);
    String linkDatasetResult =
        restTemplate
            .exchange(
                "https://api.data.mendeley.com/active-data-entities/datasets/drafts/"
                    + datasetResult.getId()
                    + "/files",
                // new URL("http://localhost:80").toURI(),
                HttpMethod.POST,
                new HttpEntity<>(
                    "{\n"
                        + "  \"ancestors\": [\n"
                        + "    {\n"
                        + "      \"parent\": {}\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"parent\": {\n"
                        + "    \"parent\": {}\n"
                        + "  },\n"
                        + "  \"ticket_id\": \""
                        + dcdFileResult.getId()
                        + "\",\n"
                        + "  \"filename\": \"Picture1.png\",\n"
                        + "  \"description\": \"immagine prova\",\n"
                        + "  \"media_type\": \"image/png\"\n"
                        + "}",
                    headers),
                String.class)
            .getBody();
    System.out.println("Link Dataset and File result: " + linkDatasetResult);
  }

  @Test
  @Ignore(
      "This test was used for the Digital Commons data POC. "
          + "We leave the test Ignored so we can potentially run it manually")
  public void testConnectController() throws Exception {
    MvcResult result =
        mockMvc
            .perform(post("/apps/digitalcommonsdata/connect").principal(user::getUsername))
            .andExpect(status().is(302))
            .andReturn();

    String redirectUrlString = result.getResponse().getRedirectedUrl();
    Map<String, String> parameters = getUrlQueryMap(redirectUrlString);

    assertNotNull(redirectUrlString);
    Optional<UserConnection> optUserConn =
        userConnectionManager.findByUserNameProviderName(
            user.getUsername(), DIGITAL_COMMONS_DATA_APP_NAME);
    assertTrue(optUserConn.isPresent());
    assertFalse(optUserConn.get().getSecret().isBlank());
    result =
        mockMvc
            .perform(
                get("/apps/digitalcommonsdata/callback")
                    .param("code", parameters.get("code"))
                    .param("state", parameters.get("state"))
                    .principal(user::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    redirectUrlString = result.getResponse().getForwardedUrl();

    // since there is no user login, the Mendeley end point returns an internal server error
    assertTrue(redirectUrlString.contains("authorizationError"));

    optUserConn =
        userConnectionManager.findByUserNameProviderName(
            user.getUsername(), DIGITAL_COMMONS_DATA_APP_NAME);
    assertTrue(optUserConn.isPresent());
    assertFalse(optUserConn.get().getSecret().isBlank());

    // since there is no user login the token has not been updated
    assertTrue(optUserConn.get().getAccessToken().startsWith("TEMP"));
  }

  private HttpHeaders addAuthorizationHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    return addAuthorizationHeaders(headers, accessToken);
  }

  private HttpHeaders addAuthorizationHeaders(HttpHeaders headers, String accessToken) {
    headers.add("Authorization", String.format("Bearer %s", accessToken));
    return headers;
  }

  public static Map<String, String> getUrlQueryMap(String query) {
    String[] params = query.split("&");
    Map<String, String> map = new HashMap<String, String>();

    for (String param : params) {
      String name = param.split("=")[0];
      String value = param.split("=")[1];
      map.put(name, value);
    }
    return map;
  }
}
