package com.researchspace.webapp.integrations.dmpassistant;

import static com.researchspace.service.IntegrationsHandler.DMPASSISTANT_APP_NAME;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.DMPManager;
import com.researchspace.service.UserConnectionManager;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Spring MVC integration test covering the multi-DMP import path through {@link
 * DMPAssistantController#importPlans}. The user's bearer token is provisioned by persisting a real
 * {@link UserConnection}; outbound calls from the provider to the DMP Assistant HTTP API are
 * intercepted with {@link MockRestServiceServer} bound to a freshly-installed {@link RestTemplate}.
 */
@WebAppConfiguration
public class DMPAssistantControllerMVCIT extends API_MVC_TestBase {

  private static final String DMP_ASSISTANT_BASE_URL = "https://dmp-pgd.ca";
  private static final String TEST_TOKEN = "test-bearer-token";

  @Autowired private UserConnectionManager userConnectionManager;
  @Autowired private DMPManager dmpManager;
  @Autowired private DMPAssistantProviderImpl dmpAssistantProvider;

  private User user;
  private MockRestServiceServer mockServer;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();

    UserConnection conn = new UserConnection();
    conn.setId(
        new UserConnectionId(user.getUsername(), DMPASSISTANT_APP_NAME, DMPASSISTANT_APP_NAME));
    conn.setAccessToken(TEST_TOKEN);
    conn.setDisplayName("DMP Assistant test token");
    conn.setExpireTime(0L);
    userConnectionManager.save(conn);

    RestTemplate restTemplate = new RestTemplate();
    dmpAssistantProvider.setRestTemplate(restTemplate);
    mockServer = MockRestServiceServer.createServer(restTemplate);
  }

  @Test
  public void importPlansImportsMultipleDmpsInOneCall() throws Exception {
    String planOne =
        "{\"dmp\":{\"title\":\"Plan One\",\"dmp_id\":"
            + "{\"identifier\":\"https://dmp-pgd.ca/api/v2/plans/101\"}}}";
    String planTwo =
        "{\"dmp\":{\"title\":\"Plan Two\",\"dmp_id\":"
            + "{\"identifier\":\"https://dmp-pgd.ca/api/v2/plans/202\"}}}";

    mockServer
        .expect(requestTo(DMP_ASSISTANT_BASE_URL + "/api/v2/plans/101?complete=true"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_TOKEN))
        .andRespond(withSuccess(planOne, MediaType.APPLICATION_JSON));
    mockServer
        .expect(requestTo(DMP_ASSISTANT_BASE_URL + "/api/v2/plans/202?complete=true"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_TOKEN))
        .andRespond(withSuccess(planTwo, MediaType.APPLICATION_JSON));

    String requestBody =
        "[{\"id\":\"101\",\"filename\":\"plan-one.json\"},"
            + "{\"id\":\"202\",\"filename\":\"plan-two.json\"}]";

    mockMvc
        .perform(
            post("/apps/dmpassistant/importPlans")
                .principal(user::getUsername)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].dmp.title").value("Plan One"))
        .andExpect(jsonPath("$.data[1].dmp.title").value("Plan Two"));

    mockServer.verify();

    List<DMPUser> savedDmps = dmpManager.findDMPsForUser(user);
    assertEquals(2, savedDmps.size());
    DMPUser dmp101 =
        savedDmps.stream()
            .filter(d -> "101".equals(d.getDmpId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected a DMPUser row for plan id 101"));
    DMPUser dmp202 =
        savedDmps.stream()
            .filter(d -> "202".equals(d.getDmpId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected a DMPUser row for plan id 202"));
    assertEquals(DMPSource.DMP_ASSISTANT, dmp101.getSource());
    assertEquals(DMPSource.DMP_ASSISTANT, dmp202.getSource());
    assertEquals("Plan One", dmp101.getTitle());
    assertEquals("Plan Two", dmp202.getTitle());
  }
}
