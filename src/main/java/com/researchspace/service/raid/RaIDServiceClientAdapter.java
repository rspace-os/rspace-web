package com.researchspace.service.raid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.researchspace.raid.client.RaIDClient;
import com.researchspace.raid.model.RaIDServicePoint;
import com.researchspace.webapp.integrations.MultiInstanceClient;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller.AccessToken;
import com.researchspace.webapp.integrations.raid.RaIDReferenceDTO;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import org.springframework.web.client.HttpServerErrorException;

public interface RaIDServiceClientAdapter extends MultiInstanceClient<RaIDServerConfigurationDTO> {

  List<RaIDServicePoint> getServicePointList(String username, String serverAlias)
      throws HttpServerErrorException, URISyntaxException, JsonProcessingException;

  RaIDServicePoint getServicePoint(String username, String serverAlias, Integer servicePointId)
      throws HttpServerErrorException;

  Set<RaIDReferenceDTO> getRaIDList(String username, String serverAlias)
      throws HttpServerErrorException, URISyntaxException, JsonProcessingException;

  RaIDReferenceDTO getRaID(
      String username, String serverAlias, String raidPrefix, String raidSuffix)
      throws HttpServerErrorException, URISyntaxException, JsonProcessingException;

  String performRedirectConnect(String serverAlias)
      throws HttpServerErrorException, URISyntaxException;

  AccessToken performCreateAccessToken(
      String username, String serverAlias, String authorizationCode)
      throws JsonProcessingException, URISyntaxException;

  AccessToken performRefreshToken(String username, String serverAlias)
      throws HttpServerErrorException, URISyntaxException, JsonProcessingException;

  boolean isRaidConnectionAlive(String username, String serverAlias);

  void clearConnectionAliveCache();

  /* test purposes */
  void setRaidClient(RaIDClient raidClient);
}
