package com.researchspace.webapp.integrations.ascenscia.client;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.webapp.integrations.ascenscia.RefreshDTO;
import com.researchspace.webapp.integrations.ascenscia.dto.AuthResponseDTO;
import com.researchspace.webapp.integrations.ascenscia.dto.ConnectDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class AscensciaClient {

  private static final String ASCENSCIA_BASE_URL = "https://ascenscia.app/api/auth/";

  private final RestTemplate restTemplate;

  public AscensciaClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public AuthResponseDTO authenticate(ConnectDTO connectDTO, User user) {
    log.info("Authenticating user {} with Ascenscia", user.getUsername());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<ConnectDTO> requestEntity = new HttpEntity<>(connectDTO, headers);

    try {
      ResponseEntity<AuthResponseDTO> response =
          restTemplate.postForEntity(
              ASCENSCIA_BASE_URL + "login", requestEntity, AuthResponseDTO.class);

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        return response.getBody();
      } else {
        throw new HttpClientErrorException(
            response.getStatusCode(), "Error authenticating with Ascenscia API");
      }
    } catch (RestClientException e) {
      log.error("Error connecting to Ascenscia API", e);
      throw new HttpClientErrorException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Error connecting to Ascenscia API");
    }
  }

  public AuthResponseDTO refreshAccessToken(UserConnection connection, User user) {
    log.info("Refreshing access token for user {} with Ascenscia", user.getUsername());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    RefreshDTO refreshDTO =
        new RefreshDTO(
            connection.getRefreshToken(), user.getUsername(), connection.getId().getProviderId());
    HttpEntity<RefreshDTO> requestEntity = new HttpEntity<>(refreshDTO, headers);

    try {
      return restTemplate
          .postForEntity(ASCENSCIA_BASE_URL + "refresh", requestEntity, AuthResponseDTO.class)
          .getBody();
      // Simulating a successful response for demonstration purposes
      //        AuthResponseDTO mockResponse = new AuthResponseDTO();
      //        mockResponse.setAccessToken("mock-jwt-token");
      //        return new ResponseEntity<>(mockResponse, HttpStatus.OK);
    } catch (RestClientException e) {
      log.error("Error connecting to Ascenscia API", e);
      throw new HttpClientErrorException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Error connecting to Ascenscia API");
    }
  }
}
