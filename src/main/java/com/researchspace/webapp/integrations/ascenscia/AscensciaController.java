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
import com.researchspace.webapp.integrations.ascenscia.exception.AscensciaConnectionNotFoundException;
import com.researchspace.webapp.integrations.ascenscia.exception.AscensciaException;
import com.researchspace.webapp.integrations.ascenscia.exception.AscensciaExceptionHandler;
import java.util.Optional;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Slf4j
@Controller
@RequestMapping("/apps/ascenscia")
public class AscensciaController extends BaseController {

  private final AscensciaClient ascensciaClient;

  private final UserConnectionManager userConnectionManager;

  private final AscensciaExceptionHandler exceptionHandler = new AscensciaExceptionHandler();

  public AscensciaController(
      AscensciaClient ascensciaClient, UserConnectionManager userConnectionManager) {
    this.ascensciaClient = ascensciaClient;
    this.userConnectionManager = userConnectionManager;
  }

  @ExceptionHandler(AscensciaException.class)
  public ResponseEntity<String> handleAscensciaException(AscensciaException e) {
    return exceptionHandler.handle(e);
  }

  @PostMapping("/connect")
  public @ResponseBody AuthResponseDTO connect(@Valid ConnectDTO connectDTO) {
    User subject = userManager.getAuthenticatedUserInSession();

    AuthResponseDTO authResponse = ascensciaClient.authenticate(connectDTO, subject);

    // should only be 1 active connection per user and provider so delete any existing
    userConnectionManager.deleteByUserAndProvider(ASCENSCIA_APP_NAME, subject.getUsername());

    UserConnection newUserConnection = new UserConnection();
    newUserConnection.setId(
        new UserConnectionId(subject.getUsername(), ASCENSCIA_APP_NAME, connectDTO.getUsername()));
    newUserConnection.setAccessToken(authResponse.getAccessToken());
    newUserConnection.setRefreshToken(authResponse.getRefreshToken());
    newUserConnection.setDisplayName("Ascenscia token");
    userConnectionManager.save(newUserConnection);

    return authResponse;
  }

  @GetMapping("/token/refresh")
  public @ResponseBody AuthResponseDTO refreshAccessCredentials() {
    User user = userManager.getAuthenticatedUserInSession();
    Optional<UserConnection> connection =
        userConnectionManager.findByUserNameProviderName(user.getUsername(), ASCENSCIA_APP_NAME);

    if (connection.isEmpty()) {
      throw new AscensciaConnectionNotFoundException(
          "No Ascenscia connection found for user " + user.getUsername());
    }

    return ascensciaClient.refreshAccessToken(connection.get(), user);
  }
}
