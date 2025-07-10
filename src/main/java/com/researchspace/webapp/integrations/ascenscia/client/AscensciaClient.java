package com.researchspace.webapp.integrations.ascenscia.client;

import com.researchspace.model.User;
import com.researchspace.webapp.integrations.ascenscia.dto.AuthResponseDTO;
import com.researchspace.webapp.integrations.ascenscia.dto.ConnectDTO;
import com.researchspace.webapp.integrations.ascenscia.exception.AscensciaException;
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
        throw new AscensciaException(
            response.getStatusCode(),
            "Error authenticating with Ascenscia API: "
                + response.getStatusCode().getReasonPhrase());
      }
    } catch (HttpClientErrorException e) {
      log.error(
          "HTTP error connecting to Ascenscia API: {} - {}",
          e.getStatusCode(),
          e.getStatusText(),
          e);
      throw new AscensciaException(
          e.getStatusCode(), "Error from Ascenscia API: " + e.getMessage(), e);
    } catch (RestClientException e) {
      log.error("Error connecting to Ascenscia API", e);
      throw new AscensciaException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Error connecting to Ascenscia API", e);
    }
  }
}
