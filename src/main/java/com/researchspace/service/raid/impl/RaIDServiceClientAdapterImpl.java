package com.researchspace.service.raid.impl;

import static com.researchspace.CacheNames.RAID_CONNECTION;
import static com.researchspace.service.IntegrationsHandler.RAID_APP_NAME;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.raid.client.RaIDClient;
import com.researchspace.raid.model.RaID;
import com.researchspace.raid.model.RaIDRelatedObject;
import com.researchspace.raid.model.RaIDServicePoint;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.raid.RaIDServerConfigurationDTO;
import com.researchspace.service.raid.RaIDServiceClientAdapter;
import com.researchspace.webapp.integrations.MultiInstanceBaseClient;
import com.researchspace.webapp.integrations.MultiInstanceClient;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller.AccessToken;
import com.researchspace.webapp.integrations.raid.RaIDReferenceDTO;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
  public static final String RAID_OAUTH_CONNECTED = "RAID_OAUTH_CONNECTED";
  public static final String RAID_ALIAS = "RAID_ALIAS";

  @Setter
  @Getter
  @Value("${raid.server.config:}")
  protected String configurationMap;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private static String URL_CALLBACK;

  private @Autowired UserConnectionManager userConnectionManager;

  @Setter // for test purposes
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
  public List<RaIDServicePoint> getServicePointList(String username, String serverAlias)
      throws HttpServerErrorException, URISyntaxException, JsonProcessingException {
    return raidClient.getServicePointList(
        getApiBaseUrl(serverAlias),
        getExistingAccessTokenOrRefreshIfExpired(username, serverAlias));
  }

  @Override
  public RaIDServicePoint getServicePoint(
      String username, String serverAlias, Integer servicePointId) throws HttpServerErrorException {
    return raidClient.getServicePoint(
        getApiBaseUrl(serverAlias),
        getExistingAccessToken(username, serverAlias),
        getServicePointId(serverAlias));
  }

  @Override
  public Set<RaIDReferenceDTO> getRaIDList(String username, String serverAlias)
      throws HttpServerErrorException, URISyntaxException, JsonProcessingException {
    List<RaID> verboseRaidList =
        raidClient.getRaIDList(
            getApiBaseUrl(serverAlias),
            getExistingAccessTokenOrRefreshIfExpired(username, serverAlias));
    return verboseRaidList.stream()
        .map(
            raid ->
                new RaIDReferenceDTO(
                    serverAlias,
                    raid.getTitle().get(0).getText(),
                    raid.getIdentifier().getId(),
                    raid.getIdentifier().getRaidAgencyUrl()))
        .collect(Collectors.toSet());
  }

  @Override
  public RaIDReferenceDTO getRaID(
      String username, String serverAlias, String raidPrefix, String raidSuffix)
      throws HttpServerErrorException, URISyntaxException, JsonProcessingException {
    RaID verboseRaid =
        raidClient.getRaID(
            getApiBaseUrl(serverAlias),
            getExistingAccessTokenOrRefreshIfExpired(username, serverAlias),
            raidPrefix,
            raidSuffix);
    return new RaIDReferenceDTO(
        serverAlias,
        verboseRaid.getTitle().get(0).getText(),
        verboseRaid.getIdentifier().getId(),
        verboseRaid.getIdentifier().getRaidAgencyUrl());
  }

  @Override
  public boolean addRaIDRelatedObject(String username, RaIDReferenceDTO raid, String doiLink)
      throws URISyntaxException, JsonProcessingException {
    RaID newUpdatedRaid =
        raidClient.addRaIDRelatedObject(
            getApiBaseUrl(raid.getRaidServerAlias()),
            getExistingAccessTokenOrRefreshIfExpired(username, raid.getRaidServerAlias()),
            raid.getRaidPrefix(),
            raid.getRaidSuffix(),
            doiLink);
    return newUpdatedRaid.getRelatedObject().contains(new RaIDRelatedObject(doiLink));
  }

  @Override
  public String performRedirectConnect(String serverAlias)
      throws HttpServerErrorException, URISyntaxException {
    return raidClient.getRedirectUriToConnect(
        getAuthBaseUrl(serverAlias), getClientId(serverAlias), URL_CALLBACK, serverAlias);
  }

  @Override
  @CacheEvict(value = RAID_CONNECTION, key = "#username + #serverAlias")
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
  @CacheEvict(value = RAID_CONNECTION, key = "#username + #serverAlias")
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
  @Cacheable(value = RAID_CONNECTION, key = "#username + #serverAlias")
  public boolean isRaidConnectionAlive(String username, String serverAlias) {
    Optional<UserConnection> optUserConnection =
        getExistingRaidUserConnection(username, serverAlias);
    if (optUserConnection.isEmpty()) {
      return false;
    }

    boolean isConnectionAlive = true;
    try {
      this.getServicePoint(username, serverAlias, getServicePointId(serverAlias));
    } catch (Exception e) {
      log.error(
          "Couldn't perform test connection action on RaID. "
              + "The connection will be flagged as NOT ALIVE",
          e);
      isConnectionAlive = false;
    }
    return isConnectionAlive;
  }

  private String getExistingAccessTokenOrRefreshIfExpired(String username, String serverAlias)
      throws URISyntaxException, JsonProcessingException {
    String accessToken = getExistingAccessToken(username, serverAlias);
    if (!isRaidConnectionAlive(username, serverAlias)) {
      accessToken = performRefreshToken(username, serverAlias).getAccessToken();
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

  private String getExistingAccessToken(String username, String serverAlias) {
    Optional<UserConnection> optUserConnection =
        getExistingRaidUserConnection(username, serverAlias);
    if (optUserConnection.isEmpty()) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "User connection not found");
    }
    if (StringUtils.isBlank(optUserConnection.get().getAccessToken())) {
      throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "Refresh token not found");
    }
    return optUserConnection.get().getAccessToken();
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
