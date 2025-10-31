package com.researchspace.service.raid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.researchspace.raid.model.RaID;
import com.researchspace.raid.model.RaIDServicePoint;
import com.researchspace.webapp.integrations.MultiInstanceClient;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller.AccessToken;
import java.net.URISyntaxException;
import java.util.Set;
import org.springframework.web.client.HttpServerErrorException;

public interface RaIDServiceClientAdapter extends MultiInstanceClient<RaIDServerConfigurationDTO> {

  Set<RaIDServicePoint> getServicePointList(String username, String serverAlias)
      throws HttpServerErrorException;

  RaIDServicePoint getServicePoint(String username, String serverAlias, Integer servicePointId)
      throws HttpServerErrorException;

  Set<RaID> getRaIDList(String username, String serverAlias) throws HttpServerErrorException;

  RaID getRaID(String username, String serverAlias, String raidPrefix, String raidSuffix)
      throws HttpServerErrorException;

  String performRedirectConnect(String serverAlias)
      throws HttpServerErrorException, URISyntaxException;

  AccessToken performCreateAccessToken(
      String username, String serverAlias, String authorizationCode)
      throws JsonProcessingException, URISyntaxException;

  AccessToken performRefreshToken(String username, String serverAlias)
      throws HttpServerErrorException, URISyntaxException, JsonProcessingException;

  Boolean isRaidConnectionAlive(String username, String serverAlias);
}
