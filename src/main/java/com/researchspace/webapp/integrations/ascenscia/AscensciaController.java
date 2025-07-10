package com.researchspace.webapp.integrations.ascenscia;

import static com.researchspace.service.IntegrationsHandler.ASCENSCIA_APP_NAME;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.webapp.controller.BaseController;
import com.researchspace.webapp.integrations.ascenscia.client.AscensciaClient;
import com.researchspace.webapp.integrations.ascenscia.dto.AuthResponseDTO;
import com.researchspace.webapp.integrations.ascenscia.dto.ConnectDTO;
import com.researchspace.webapp.integrations.ascenscia.exception.AscensciaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/apps/ascenscia")
public class AscensciaController extends BaseController {

  private final AscensciaClient ascensciaClient;

  private final UserConnectionManager userConnectionManager;

  public AscensciaController(
      AscensciaClient ascensciaClient, UserConnectionManager userConnectionManager) {
    this.ascensciaClient = ascensciaClient;
    this.userConnectionManager = userConnectionManager;
  }

  @PostMapping("/connect")
  public ResponseEntity<?> connect(ConnectDTO connectDTO) {
    try {
      validateParams(connectDTO);
      User subject = userManager.getAuthenticatedUserInSession();
      AuthResponseDTO authResponse = ascensciaClient.authenticate(connectDTO, subject);

      saveToken(connectDTO.getUsername(), subject, authResponse);

      return new ResponseEntity<>(authResponse, HttpStatus.CREATED);
    } catch (AscensciaException e) {
      log.error("Error connecting to Ascenscia: {}", e.getMessage());
      return new ResponseEntity<>(e.getMessage(), e.getStatus());
    } catch (Exception e) {
      log.error("Error connecting to Ascenscia: {}", e.getMessage(), e);
      return new ResponseEntity<>("Unable to generate API key", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private void validateParams(ConnectDTO connectDTO) {
    if (connectDTO == null) {
      throw new AscensciaException(HttpStatus.BAD_REQUEST, "ConnectDTO must not be null");
    }
    if (connectDTO.getUsername().isBlank()) {
      throw new AscensciaException(HttpStatus.BAD_REQUEST, "Username must not be empty");
    }
    if (connectDTO.getPassword().isBlank()) {
      throw new AscensciaException(HttpStatus.BAD_REQUEST, "Password must not be empty");
    }
    if (connectDTO.getOrganization().isBlank()) {
      throw new AscensciaException(HttpStatus.BAD_REQUEST, "Organization must not be empty");
    }
  }

  private void saveToken(String username, User subject, AuthResponseDTO authResponse) {
    // should only be 1 active connection per user and provider, so delete any existing
    userConnectionManager.deleteByUserAndProvider(ASCENSCIA_APP_NAME, subject.getUsername());

    UserConnection newUserConnection = new UserConnection();
    newUserConnection.setId(
        new UserConnectionId(subject.getUsername(), ASCENSCIA_APP_NAME, username));
    newUserConnection.setAccessToken(authResponse.getAccessToken());
    newUserConnection.setRefreshToken(authResponse.getRefreshToken());
    newUserConnection.setDisplayName("Ascenscia token");
    userConnectionManager.save(newUserConnection);
  }
}
