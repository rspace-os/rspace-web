package com.researchspace.webapp.integrations.dcd;

import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.webapp.integrations.dcd.DigitalCommonsDataController.AccessToken;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@WebAppConfiguration
public class DigitalCommonsDataMVCIT extends API_MVC_TestBase {

  @Value("${dcd.client.id}")
  private String clientId;

  @Value("${dcd.client.secret}")
  private String clientSecret;

  @Value("${dcd.auth.base.url}")
  private URL authBaseUrl;

  private RestTemplate restTemplate;
  private @Autowired DigitalCommonsDataManager digitalCommonsDataManager;
  private @Autowired UserConnectionManager userConnectionManager;

  private User user;
  private PaginationCriteria<BaseRecord> pgcrit = null;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
    pgcrit = PaginationCriteria.createDefaultForClass(BaseRecord.class);
  }

  @Test
  public void testGetAccessToken() throws Exception {
    MvcResult result =
        mockMvc
            .perform(post("/apps/dcd/connect").principal(user::getUsername))
            .andExpect(status().isOk())
            .andReturn();

    assertEquals("connect/dcd/connected", result.getModelAndView().getViewName());

    String assessToken =
        userConnectionManager
            .findByUserNameProviderName(user.getUsername(), DIGITAL_COMMONS_DATA_APP_NAME)
            .get()
            .getAccessToken();
    assertNotNull(assessToken);
    assertFalse(assessToken.isBlank());
  }

  @Test
  //  @Ignore
  // TODO[nik]: Still needs to be implemented
  public void testGetDatasetsAndById() throws IOException, URISyntaxException {
    RestTemplate restTemplate = new RestTemplate();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setBasicAuth(
        "kW1WtsEeW7b9LugvT-jq9148gmkTPDeFFZe4NM9mV1k",
        "Q5QHqNn91Q2tvJwsqkRDorAS9i6Mg685GLY6TYLBWH22MITQ4CvOnRx3kQ43IKAr");

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "client_credentials");
    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

    ////////// get access token //////////
    AccessToken accessToken =
        restTemplate
            .exchange(
                "https://auth.data.mendeley.com/oauth2/token",
                HttpMethod.POST,
                request,
                AccessToken.class)
            .getBody();
    System.out.println("AccessToken: " + accessToken);

    ////////// get the datasets //////////
    String jsonResult =
        restTemplate
            .exchange(
                new URL("https://api.data.mendeley.com/datasets").toURI(),
                //       new URL("http://localhost:80").toURI(),
                HttpMethod.GET,
                new HttpEntity<>(getHttpHeaders(accessToken.getAccessToken())),
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
                new HttpEntity<>(getHttpHeaders(accessToken.getAccessToken())),
                String.class)
            .getBody();
    System.out.println("Dataset[jxj8bn2vyf]: " + jsonResult);

    /////// create a draft dataset   ////////
    // TODO[nik]
    headers = getHttpHeaders(accessToken.getAccessToken());
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

    /////// upload a file into the dataset  //////////
    // TODO[nik]

    ////////// upload a file /////////////
    //    FileSystemResource file =
    //        new FileSystemResource(new File("src/test/resources/TestResources/Picture1.png"));
    ////    File file =
    ////        new File("src/test/resources/TestResources/csv.csv");
    //
    //    ContentDisposition contentDisposition =   ContentDisposition.builder("attachment")
    //        .name("Picture1.png").filename("Picture1.png").build();
    //
    //    headers = new HttpHeaders();
    //    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    //    headers.setContentLength(file.contentLength());
    //    headers.setContentDisposition(contentDisposition);
    //    headers.add("Authorization",
    //        String.format("Bearer %s", accessToken.getAccessToken()));
    //
    //    jsonResult = restTemplate.exchange(
    //        "https://uploads.data.mendeley.com/uploads",
    ////        "http://localhost:80"),
    //        HttpMethod.POST,
    //        new HttpEntity<>(file, headers),
    //        String.class
    //    ).getBody();

    //// another method
    //    jsonResult = restTemplate.postForEntity("https://uploads.data.mendeley.com/uploads",
    //        uploadRequestEntity,
    //        String.class
    //    ).getBody();

    /// another method
    //    byte[] fileContent = FileUtils.readFileToByteArray(
    //        new File("src/test/resources/TestResources/Picture1.png"));
    //    String base64Image = Base64.getEncoder().encodeToString(fileContent);
    //    jsonResult = restTemplate.postForEntity(
    ////        "http://localhost:80",
    //        "https://uploads.data.mendeley.com/uploads",
    //        constructRequest(base64Image, accessToken.getAccessToken(), file.contentLength()),
    //        String.class).getBody();

    System.out.println("Upload file: " + jsonResult);
  }

  private HttpEntity<MultiValueMap<String, Object>> constructRequest(
      String base64Image, String accessToken, long size) throws IOException {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add(
        "file",
        Base64Util.convertToHttpEntity(
            Base64Util.generateFilename(base64Image), Base64Util.stripStartBase64(base64Image)));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    headers.setContentLength(5);
    headers.add("Authorization", String.format("Bearer %s", accessToken));

    return new HttpEntity<>(body, headers);
  }

  @Test
  @Ignore
  public void testUploadFile() throws Exception {
    User user = createAndSaveUser(CoreTestUtils.getRandomName(8));
    logoutAndLoginAs(user);
    MockMultipartFile mf =
        new MockMultipartFile(
            "imageFile", "image.png", "png", getTestResourceFileStream("Picture1.png"));
    long size = mf.getSize();
    mockMvc
        .perform(
            fileUpload("/userform/profileImage/upload")
                .file(mf)
                .principal(new MockPrincipal(user.getUsername())))
        .andExpect(status().isOk())
        .andReturn();
  }

  private HttpHeaders getHttpHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    //    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.add("Authorization", String.format("Bearer %s", accessToken));
    return headers;
  }
}
