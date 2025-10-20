package com.researchspace.service.raid.impl;

import static com.researchspace.service.IntegrationsHandler.RAID_APP_NAME;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.raid.client.RaIDClient;
import com.researchspace.raid.model.RaID;
import com.researchspace.raid.model.RaIDServicePoint;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.raid.RaIDServerConfigurationDTO;
import com.researchspace.service.raid.RaIDServiceClientAdapter;
import com.researchspace.webapp.integrations.MultiInstanceBaseClient;
import com.researchspace.webapp.integrations.MultiInstanceClient;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller.AccessToken;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@Slf4j
@Service
@NoArgsConstructor
public class RaIDServiceClientAdapterImpl
    extends MultiInstanceBaseClient<RaIDServerConfigurationDTO>
    implements MultiInstanceClient<RaIDServerConfigurationDTO>, RaIDServiceClientAdapter {

  public static final String RAID_CONFIGURED_SERVERS = "RAID_CONFIGURED_SERVERS";
  public static final String RAID_URL = "RAID_URL";
  public static final String RAID_OAUTH_CONNECTED = "RAID_OAUTH_CONNECTED";
  public static final String RAID_ALIAS = "RAID_ALIAS";

  @Setter
  @Getter
  @Value("${raid.server.config:}")
  protected String configurationMap;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private static String URL_CALLBACK;

  private @Autowired UserConnectionManager userConnectionManager;
  private @Autowired UserManager userManager;

  @Setter(value = AccessLevel.PROTECTED) // for test purposes
  private @Autowired RaIDClient raidClient;

  private @Autowired IPropertyHolder properties;

  @PostConstruct
  public void init() throws URISyntaxException {
    URL_CALLBACK =
        new URI(properties.getServerUrl() + "/apps/raid/callback").normalize().toString();
  }

  @Override
  protected TypeReference<Map<String, RaIDServerConfigurationDTO>> getTypeReference() {
    return new TypeReference<>() {};
  }

  @Override
  public Set<RaIDServicePoint> getServicePointList(String username, String serverAlias)
      throws HttpServerErrorException {
    // TODO: RSDEV-849
    return null;
  }

  @Override
  public RaIDServicePoint getServicePoint(
      String username, String serverAlias, Integer servicePointId) throws HttpServerErrorException {
    // TODO: RSDEV-849
    RaIDServicePoint result = new RaIDServicePoint(); // TODO: current dummy one - TBD on RSDEV-849
    return null;
  }

  @Override
  public Set<RaID> getRaIDList(String username, String serverAlias)
      throws HttpServerErrorException {
    // TODO: RSDEV-849
    return null;
  }

  @Override
  public RaID getRaID(String username, String serverAlias, String raidPrefix, String raidSuffix)
      throws HttpServerErrorException {
    // TODO: RSDEV-849
    return null;
  }

  @Override
  public String performRedirectConnect(String serverAlias)
      throws HttpServerErrorException, URISyntaxException {
    return raidClient.getRedirectUriToConnect(
        getAuthBaseUrl(serverAlias), getClientId(serverAlias), URL_CALLBACK, serverAlias);
  }

  @Override
  public AccessToken performCreateAccessToken(
      String username, String serverAlias, String authorizationCode)
      throws JsonProcessingException, URISyntaxException {
    AccessToken accessToken =
        objectMapper.readValue(
            raidClient.getAccessToken(
                getAuthBaseUrl(serverAlias),
                getClientId(serverAlias),
                getClientSecret(serverAlias),
                authorizationCode,
                URL_CALLBACK),
            AccessToken.class);
    saveRaidUserConnection(username, accessToken, serverAlias);
    return accessToken;
  }

  @Override
  public AccessToken performRefreshToken(String username, String serverAlias)
      throws HttpServerErrorException, URISyntaxException, JsonProcessingException {

    AccessToken accessTokenRefreshed =
        objectMapper.readValue(
            raidClient.getRefreshToken(
                getAuthBaseUrl(serverAlias),
                getClientId(serverAlias),
                getClientSecret(serverAlias),
                getExistingRefreshToken(username, serverAlias),
                URL_CALLBACK),
            AccessToken.class);
    updateRaidUserConnection(username, accessTokenRefreshed, serverAlias);
    return accessTokenRefreshed;
  }

  @Override
  public Boolean isRaidConnectionAlive(String username, String serverAlias) {
    Optional<UserConnection> optUserConnection =
        getExistingRaidUserConnection(username, serverAlias);
    if (optUserConnection.isEmpty()) {
      return Boolean.FALSE;
    }

    Boolean isConnectionAlive = Boolean.TRUE;
    try {
      this.getServicePoint(username, serverAlias, getServicePointId(serverAlias));
    } catch (Exception e) {
      log.error(
          "Couldn't perform test connection action on RaID. "
              + "The connection will be flagged as NOT ALIVE",
          e);
      isConnectionAlive = Boolean.FALSE;
    }
    return isConnectionAlive;
  }

  // do not delete since it will be used by RSDEV-849
  private String getExistingAccessToken(String username, String serverAlias)
      throws URISyntaxException, JsonProcessingException {
    Optional<UserConnection> optUserConnection =
        getExistingRaidUserConnection(username, serverAlias);
    if (optUserConnection.isEmpty()) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "RaID User connection not found");
    }
    if (StringUtils.isBlank(optUserConnection.get().getAccessToken())) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "RaID Access token not found");
    }
    String accessToken;
    if (!isRaidConnectionAlive(username, serverAlias)) {
      accessToken = performRefreshToken(username, serverAlias).getAccessToken();
    } else {
      accessToken = optUserConnection.get().getAccessToken();
    }
    return accessToken;
  }

  private String getExistingRefreshToken(String username, String serverAlias) {
    Optional<UserConnection> optUserConnection =
        getExistingRaidUserConnection(username, serverAlias);
    if (optUserConnection.isEmpty()) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "User connection not found");
    }
    if (StringUtils.isBlank(optUserConnection.get().getRefreshToken())) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "Refresh token not found");
    }
    return optUserConnection.get().getRefreshToken();
  }

  private Optional<UserConnection> getExistingRaidUserConnection(
      String username, String serverAlias) {
    Optional<UserConnection> optConn =
        userConnectionManager.findByUserNameProviderName(username, RAID_APP_NAME, serverAlias);
    if (optConn.isEmpty()) {
      log.error("No RaID connection found for user {}", username);
    }
    return optConn;
  }

  private void saveRaidUserConnection(
      String username, AccessToken accessToken, String serverAlias) {

    Optional<UserConnection> existingConnection =
        userConnectionManager.findByUserNameProviderName(username, RAID_APP_NAME, serverAlias);
    UserConnection conn = existingConnection.orElse(new UserConnection());
    conn.setId(new UserConnectionId(username, RAID_APP_NAME, serverAlias));
    conn.setAccessToken(accessToken.getAccessToken());
    conn.setRefreshToken(accessToken.getRefreshToken());
    conn.setExpireTime(getExpireTime(accessToken.getExpiresIn()));
    conn.setDisplayName(RAID_APP_NAME + " Access Token");
    Optional<Integer> existingMaxRank =
        userConnectionManager.findMaxRankByUserNameProviderName(username, RAID_APP_NAME);
    if (existingMaxRank.isPresent()) {
      conn.setRank(existingMaxRank.get() + 1);
    } else {
      conn.setRank(1);
    }
    userConnectionManager.save(conn);
  }

  private void updateRaidUserConnection(
      String username, AccessToken accessToken, String serverAlias) {
    Optional<UserConnection> optUserConnection =
        getExistingRaidUserConnection(username, serverAlias);
    if (optUserConnection.isEmpty()) {
      throw new HttpClientErrorException(
          HttpStatus.NOT_FOUND, "No RaID connection found for the user " + username);
    }

    UserConnection userConnection = optUserConnection.get();
    userConnection.setAccessToken(accessToken.getAccessToken());
    userConnection.setRefreshToken(accessToken.getRefreshToken());
    userConnection.setExpireTime(getExpireTime(accessToken.getExpiresIn()));
    userConnection.setDisplayName("RaID refreshed access token");
    userConnectionManager.save(userConnection);
    log.info("Token refreshed for RaID for user {}", username);
  }

  private long getExpireTime(Long expiresIn) {
    return Instant.now().toEpochMilli() + (expiresIn * 1000);
  }

  @NotNull
  private String getClientId(String serverAlias) {
    if (!this.getServerMapByAlias().containsKey(serverAlias)
        || StringUtils.isBlank(this.getServerMapByAlias().get(serverAlias).getClientId())) {
      throw new HttpClientErrorException(
          HttpStatus.NOT_FOUND, "RaID clientId for alias=\"" + serverAlias + "\" not found");
    }
    return this.getServerMapByAlias().get(serverAlias).getClientId();
  }

  @NotNull
  private String getClientSecret(String serverAlias) {
    if (!this.getServerMapByAlias().containsKey(serverAlias)
        || StringUtils.isBlank(this.getServerMapByAlias().get(serverAlias).getClientSecret())) {
      throw new HttpClientErrorException(
          HttpStatus.NOT_FOUND, "RaID clientSecret for alias=\"" + serverAlias + "\" not found");
    }
    return this.getServerMapByAlias().get(serverAlias).getClientSecret();
  }

  @NotNull
  private Integer getServicePointId(String serverAlias) {
    if (!this.getServerMapByAlias().containsKey(serverAlias)
        || this.getServerMapByAlias().get(serverAlias).getServicePointId() == null) {
      throw new HttpClientErrorException(
          HttpStatus.NOT_FOUND, "RaID servicePointId for alias=\"" + serverAlias + "\" not found");
    }
    return this.getServerMapByAlias().get(serverAlias).getServicePointId();
  }
}
