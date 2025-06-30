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
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.HttpClientErrorException;

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
  public ResponseEntity<?> connect(@Valid ConnectDTO connectDTO) {
    User subject = userManager.getAuthenticatedUserInSession();

    try {
      ResponseEntity<AuthResponseDTO> response = ascensciaClient.authenticate(connectDTO, subject);

      if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
        String token = response.getBody().getAccessToken();

        UserConnection connection = new UserConnection();
        connection.setId(
            new UserConnectionId(
                subject.getUsername(), ASCENSCIA_APP_NAME, connectDTO.getUsername()));
        connection.setAccessToken(token);
        connection.setRefreshToken(response.getBody().getRefreshToken());
        connection.setDisplayName("Ascenscia token");
        userConnectionManager.save(connection);

        return ResponseEntity.ok().build();
      } else {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Failed to connect to Ascenscia");
      }
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
      } else {
        return ResponseEntity.status(e.getStatusCode())
            .body(e.getStatusCode() + " " + e.getStatusText());
      }
    } catch (Exception e) {
      log.error("Error connecting to Ascenscia", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("An error occurred while connecting to Ascenscia");
    }
  }
}
