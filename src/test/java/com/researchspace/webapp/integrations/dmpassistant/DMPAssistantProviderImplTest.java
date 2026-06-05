package com.researchspace.webapp.integrations.dmpassistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Pure unit tests for the DMP Assistant API client. Each test stands up a Spring {@link
 * MockRestServiceServer} bound to the provider's {@link RestTemplate}, sets one expectation, calls
 * the provider method, and asserts the request was correct and the response propagated.
 */
class DMPAssistantProviderImplTest {

  private static final String BASE_URL = "https://dmp-pgd.ca";
  private static final String TOKEN = "test-access-token";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private DMPAssistantProviderImpl provider;
  private RestTemplate restTemplate;
  private MockRestServiceServer mockServer;

  @BeforeEach
  void setUp() throws Exception {
    restTemplate = new RestTemplate();
    provider = new DMPAssistantProviderImpl();
    ReflectionTestUtils.setField(provider, "restTemplate", restTemplate);
    ReflectionTestUtils.setField(provider, "baseUrl", BASE_URL);
    provider.init();
    mockServer = MockRestServiceServer.createServer(restTemplate);
  }

  @Test
  void meSendsBearerTokenAndParsesProfile() throws Exception {
    String body =
        "{\"firstname\":\"DMP\",\"surname\":\"Administrator\",\"email\":\"u@example.ca\","
            + "\"organisation\":\"Digital Research Alliance\"}";
    mockServer
        .expect(requestTo(BASE_URL + "/api/v2/me"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    JsonNode response = provider.me(TOKEN);

    mockServer.verify();
    assertEquals("u@example.ca", response.get("email").asText());
  }

  @Test
  void listPlansForwardsPaginationAndCompleteFlag() throws Exception {
    String body = "{\"items\":[]}";
    mockServer
        .expect(requestTo(BASE_URL + "/api/v2/plans?page=2&per_page=25&complete=true"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    JsonNode response = provider.listPlans("2", "25", true, TOKEN);

    mockServer.verify();
    assertEquals(0, response.get("items").size());
  }

  @Test
  void listPlansOmitsCompleteWhenNull() throws Exception {
    mockServer
        .expect(requestTo(BASE_URL + "/api/v2/plans?page=1&per_page=20"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    provider.listPlans("1", "20", null, TOKEN);

    mockServer.verify();
  }

  @Test
  void getPlanByIdRequestsTheRightPathAndBearerToken() throws Exception {
    String body = "{\"dmp\":{\"title\":\"A plan\"}}";
    mockServer
        .expect(requestTo(BASE_URL + "/api/v2/plans/42?complete=true"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    JsonNode response = provider.getPlanById("42", true, TOKEN);

    mockServer.verify();
    assertEquals("A plan", response.get("dmp").get("title").asText());
  }

  @Test
  void createPlanPostsJsonBodyWithBearerToken() throws Exception {
    JsonNode plan = MAPPER.readTree("{\"dmp\":{\"title\":\"Example DMP\",\"language\":\"eng\"}}");
    String responseBody = "{\"dmp\":{\"id\":99,\"title\":\"Example DMP\"}}";

    mockServer
        .expect(requestTo(BASE_URL + "/api/v2/plans"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
        .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        .andRespond(
            withStatus(HttpStatus.CREATED)
                .body(responseBody)
                .contentType(MediaType.APPLICATION_JSON));

    JsonNode response = provider.createPlan(plan, TOKEN);

    mockServer.verify();
    assertEquals(99, response.get("dmp").get("id").asInt());
  }

  @Test
  void editPlanAnswersPutsAnswersBodyAtPlanIdPath() throws Exception {
    JsonNode answers = MAPPER.readTree("{\"answers\":[{\"question_id\":101,\"value\":\"x\"}]}");
    mockServer
        .expect(requestTo(BASE_URL + "/api/v2/plans/7"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
        .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

    JsonNode response = provider.editPlanAnswers("7", answers, TOKEN);

    mockServer.verify();
    assertEquals(true, response.get("ok").asBoolean());
  }

  @Test
  void listTemplatesGetsTemplatesPath() throws Exception {
    mockServer
        .expect(requestTo(BASE_URL + "/api/v2/templates"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
        .andRespond(withSuccess("{\"items\":[{\"id\":1}]}", MediaType.APPLICATION_JSON));

    JsonNode response = provider.listTemplates(TOKEN);

    mockServer.verify();
    assertEquals(1, response.get("items").get(0).get("id").asInt());
  }

  @Test
  void getTemplateByIdHitsTemplateIdPath() throws Exception {
    mockServer
        .expect(requestTo(BASE_URL + "/api/v2/templates/5"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
        .andRespond(withSuccess("{\"id\":5}", MediaType.APPLICATION_JSON));

    JsonNode response = provider.getTemplateById("5", TOKEN);

    mockServer.verify();
    assertEquals(5, response.get("id").asInt());
  }
}
