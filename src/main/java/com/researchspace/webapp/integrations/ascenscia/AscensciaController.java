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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

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
  public @ResponseBody AuthResponseDTO connect(@Valid ConnectDTO connectDTO) {
    User subject = userManager.getAuthenticatedUserInSession();

    try {
      AuthResponseDTO authResponse = ascensciaClient.authenticate(connectDTO, subject);

        String token = authResponse.getAccessToken();

        UserConnection connection = new UserConnection();
        connection.setId(
            new UserConnectionId(
                subject.getUsername(), ASCENSCIA_APP_NAME, connectDTO.getUsername()));
        connection.setAccessToken(token);
        connection.setRefreshToken(authResponse.getRefreshToken());
        connection.setDisplayName("Ascenscia token");
        userConnectionManager.save(connection);

        return authResponse;

    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
        throw new RuntimeException("Invalid credentials");
      } else {
        throw new RuntimeException(e.getStatusCode() + " " + e.getStatusText());
      }
    } catch (Exception e) {
      log.error("Error connecting to Ascenscia", e);
      throw new RuntimeException("An error occurred while connecting to Ascenscia", e);
    }
  }

  @GetMapping("/refreshToken")
  public @ResponseBody AuthResponseDTO refreshAccessCredentials() {
    User user = userManager.getAuthenticatedUserInSession();
    Optional<UserConnection> connection =
        userConnectionManager.findByUserNameProviderName(
            ASCENSCIA_APP_NAME, user.getUsername(), user.getUsername());

    if (connection.isEmpty()) {
      throw new IllegalStateException("No Ascenscia connection found for user " + user.getUsername());
    }

    try {
      return ascensciaClient.refreshAccessToken(connection.get(), user);
    } catch (HttpClientErrorException e) {
      log.error("Error refreshing Ascenscia access token", e);
      throw new RuntimeException("Failed to refresh access token: " + e.getMessage(), e);
    }
  }
}
