package com.researchspace.webapp.integrations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
public abstract class MultiInstanceBaseClient<T extends ServerConfigurationDTO>
    implements MultiInstanceClient<T> {

  // the server specific deployment.property configuration
  public abstract String getConfigurationMap();

  private Map<String, T> serverByAlias;

  protected abstract TypeReference<Map<String, T>> getTypeReference();

  @PostConstruct
  private void init() throws JsonProcessingException {
    if (StringUtils.isBlank(this.getConfigurationMap())) {
      this.serverByAlias = new HashMap<>();
    } else {
      ObjectMapper objectMapper = new ObjectMapper();
      serverByAlias = objectMapper.readValue(this.getConfigurationMap(), this.getTypeReference());
      for (Entry<String, T> serverConfEntry : serverByAlias.entrySet()) {
        serverConfEntry.getValue().setAlias(serverConfEntry.getKey());
      }
      serverByAlias = Collections.unmodifiableMap(this.serverByAlias);
    }
  }

  public Map<String, T> getServerMapByAlias() {
    if (this.serverByAlias == null) {
      try {
        this.init();
      } catch (JsonProcessingException e) {
        log.error("Unable to build the map of servers configured for this integration: ", e);
        throw new RuntimeException(
            "Unable to build the map of servers configured for this integration: ", e);
      }
    }
    return this.serverByAlias;
  }

  public T getServerConfiguration(@NotNull String serverAlias) {
    if (!this.getServerMapByAlias().containsKey(serverAlias)
        || StringUtils.isBlank(this.getServerMapByAlias().get(serverAlias).getUrl())) {
      throw new HttpClientErrorException(
          HttpStatus.NOT_FOUND, "Server configuration for alias=\"" + serverAlias + "\" not found");
    }
    return this.getServerMapByAlias().get(serverAlias);
  }

  public String getApiBaseUrl(@NotNull String serverAlias) {
    if (!this.getServerMapByAlias().containsKey(serverAlias)
        || StringUtils.isBlank(this.getServerMapByAlias().get(serverAlias).getUrl())) {
      throw new HttpClientErrorException(
          HttpStatus.NOT_FOUND, "Server apiUrl for alias=\"" + serverAlias + "\" not found");
    }
    return this.getServerMapByAlias().get(serverAlias).getUrl();
  }

  public String getAuthBaseUrl(@NotNull String serverAlias) {
    if (!this.getServerMapByAlias().containsKey(serverAlias)
        || StringUtils.isBlank(this.getServerMapByAlias().get(serverAlias).getAuthUrl())) {
      throw new HttpClientErrorException(
          HttpStatus.NOT_FOUND, "Server authUrl for alias=\"" + serverAlias + "\" not found");
    }
    return this.getServerMapByAlias().get(serverAlias).getAuthUrl();
  }
}
