package com.researchspace.webapp.integrations.dbrepo;

import static com.researchspace.model.dto.IntegrationInfo.getAppNameFromIntegrationName;
import static com.researchspace.service.IntegrationsHandler.DBREPO_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DBREPO_URL;

import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElement;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.IgnoreInLoggingInterceptor;
import com.researchspace.webapp.integrations.helper.ConnectionResultPage;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpStatusCodeException;

@Slf4j
@Controller
@RequestMapping("/apps/dbrepo")
public class DBRepoController {

  static final String CONNECTION_DISCRIMINANT = "dbrepo";
  private static final String CONNECTION_CHANNEL = "rspace.apps.dbrepo.connection";
  private static final String CONNECTION_TYPE = "DBREPO_CONNECTED";

  private final DBRepoClient dbRepoClient;
  private final IntegrationsHandler integrationsHandler;
  private final MessageSourceUtils messages;
  private final UserAppConfigManager userAppConfigManager;
  private final UserConnectionManager userConnectionManager;
  private final UserManager userManager;

  public DBRepoController(
      DBRepoClient dbRepoClient,
      IntegrationsHandler integrationsHandler,
      MessageSourceUtils messages,
      UserAppConfigManager userAppConfigManager,
      UserConnectionManager userConnectionManager,
      UserManager userManager) {
    this.dbRepoClient = dbRepoClient;
    this.integrationsHandler = integrationsHandler;
    this.messages = messages;
    this.userAppConfigManager = userAppConfigManager;
    this.userConnectionManager = userConnectionManager;
    this.userManager = userManager;
  }

  @PostMapping("/connect")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"dbrepoPassword"})
  public String connect(
      @RequestParam String dbrepoUrl,
      @RequestParam String dbrepoUsername,
      @RequestParam String dbrepoPassword,
      Principal principal,
      Model model) {
    try {
      String normalizedUrl = dbRepoClient.normalizeBaseUrl(dbrepoUrl);
      DBRepoCredentials credentials = new DBRepoCredentials(dbrepoUsername, dbrepoPassword);
      dbRepoClient.listDatabases(normalizedUrl, credentials);
      User user = userManager.getUserByUsername(principal.getName());
      saveUrl(user, normalizedUrl);
      saveCredentials(user, credentials);
      ConnectionResultPage.addConnectionAttributes(
          model, "DBRepo", CONNECTION_CHANNEL, CONNECTION_TYPE);
    } catch (Exception e) {
      log.warn("Could not connect to DBRepo for user {}", principal.getName(), e);
      OauthAuthorizationError error =
          OauthAuthorizationError.builder()
              .appName("DBRepo")
              .errorMsg(messages.getMessage("apps.dbrepo.errors.connect"))
              .errorDetails(connectionErrorDetails(e))
              .build();
      ConnectionResultPage.addError(model, "DBRepo", CONNECTION_CHANNEL, CONNECTION_TYPE, error);
    }
    return ConnectionResultPage.VIEW;
  }

  @DeleteMapping("/connect")
  public @ResponseBody ResponseEntity<Void> disconnect(Principal principal) {
    userConnectionManager.deleteByUserAndProvider(principal.getName(), DBREPO_APP_NAME);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/databases")
  public @ResponseBody ResponseEntity<List<DBRepoDatabaseDTO>> databases(Principal principal) {
    User user = userManager.getUserByUsername(principal.getName());
    Optional<String> baseUrl = findUrl(user);
    Optional<UserConnection> connection =
        userConnectionManager.findByUserNameProviderName(
            principal.getName(), DBREPO_APP_NAME, CONNECTION_DISCRIMINANT);
    if (baseUrl.isEmpty() || connection.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    DBRepoCredentials credentials =
        DBRepoCredentials.deserialize(connection.get().getAccessToken());
    return ResponseEntity.ok(dbRepoClient.listDatabases(baseUrl.get(), credentials));
  }

  @GetMapping("/databases/{databaseId}/resources")
  public @ResponseBody ResponseEntity<DBRepoDatabaseResourcesDTO> resources(
      @PathVariable String databaseId, Principal principal) {
    User user = userManager.getUserByUsername(principal.getName());
    Optional<String> baseUrl = findUrl(user);
    Optional<UserConnection> connection =
        userConnectionManager.findByUserNameProviderName(
            principal.getName(), DBREPO_APP_NAME, CONNECTION_DISCRIMINANT);
    if (baseUrl.isEmpty() || connection.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    DBRepoCredentials credentials =
        DBRepoCredentials.deserialize(connection.get().getAccessToken());
    return ResponseEntity.ok(
        dbRepoClient.listDatabaseResources(baseUrl.get(), databaseId, credentials));
  }

  private void saveUrl(User user, String normalizedUrl) {
    Long optionsId = findFirstOptionsId(user).orElse(null);
    integrationsHandler.saveAppOptions(
        optionsId, Map.of(DBREPO_URL, normalizedUrl), DBREPO_APP_NAME, false, user);
  }

  private Optional<Long> findFirstOptionsId(User user) {
    return getUserAppConfig(user).getAppConfigElementSets().stream()
        .map(AppConfigElementSet::getId)
        .findFirst();
  }

  private Optional<String> findUrl(User user) {
    return getUserAppConfig(user).getAppConfigElementSets().stream()
        .flatMap(set -> set.getConfigElements().stream())
        .filter(element -> DBREPO_URL.equals(propertyName(element)))
        .map(AppConfigElement::getValue)
        .filter(StringUtils::isNotBlank)
        .findFirst();
  }

  private String propertyName(AppConfigElement element) {
    return element.getAppConfigElementDescriptor().getDescriptor().getName();
  }

  private UserAppConfig getUserAppConfig(User user) {
    return userAppConfigManager.getByAppName(getAppNameFromIntegrationName(DBREPO_APP_NAME), user);
  }

  private void saveCredentials(User user, DBRepoCredentials credentials) {
    Optional<UserConnection> existing =
        userConnectionManager.findByUserNameProviderName(
            user.getUsername(), DBREPO_APP_NAME, CONNECTION_DISCRIMINANT);
    UserConnection connection = existing.orElse(new UserConnection());
    if (existing.isEmpty()) {
      connection.setId(
          new UserConnectionId(user.getUsername(), DBREPO_APP_NAME, CONNECTION_DISCRIMINANT));
      connection.setDisplayName("DBRepo credentials");
      connection.setRank(1);
      connection.setExpireTime(0L);
    }
    connection.setAccessToken(credentials.serialize());
    userConnectionManager.save(connection);
  }

  private String connectionErrorDetails(Exception e) {
    if (e instanceof IllegalArgumentException) {
      return messages.getMessage("apps.dbrepo.errors.invalidUrl");
    }
    if (e instanceof HttpStatusCodeException httpStatusCodeException) {
      return messages.getMessage(
          "apps.dbrepo.errors.status",
          new Object[] {String.valueOf(httpStatusCodeException.getStatusCode().value())});
    }
    return StringUtils.defaultIfBlank(
        e.getMessage(), messages.getMessage("apps.dbrepo.errors.unknown"));
  }
}
