package com.researchspace.integrations.clustermarket.client;

import static com.researchspace.service.IntegrationsHandler.CLUSTERMARKET_APP_NAME;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.testutils.TestFactory;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class ClustermarketClientImplTest {

  @Mock private UserConnectionManager userConnectionManager;

  @Test
  void detailsRequestsSendTheStoredTokenNotTheMask() {
    ClustermarketClientImpl client = new ClustermarketClientImpl(userConnectionManager);
    RestTemplate restTemplate = new RestTemplate();
    ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    ReflectionTestUtils.setField(client, "clustermarketApiUrl", "https://cm.example/api/");
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    User user = TestFactory.createAnyUser("cmUser");
    UserConnection connection = new UserConnection();
    connection.setAccessToken("stored-token");
    when(userConnectionManager.findByUserNameProviderName("cmUser", CLUSTERMARKET_APP_NAME))
        .thenReturn(Optional.of(connection));
    server
        .expect(requestTo("https://cm.example/api/bookings/7"))
        .andExpect(header("Authorization", "Bearer stored-token"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://cm.example/api/equipment/9"))
        .andExpect(header("Authorization", "Bearer stored-token"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    client.getBookingDetails("7", user);
    client.getEquipmentDetails("9", user);

    server.verify();
  }
}
